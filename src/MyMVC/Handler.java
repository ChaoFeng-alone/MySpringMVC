package MyMVC;

import com.alibaba.fastjson.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.*;
import java.util.*;

class Handler {
    //请求类名与类全名的映射
    private Map<String, String> requestClassName = new HashMap<>();
    //类全名与类实例的对应
    private Map<String, Object> requestClass = new HashMap<>();
    //实例对象与其方法列表的关系
    private Map<Object, Map<String, Method>> objectMethodMap = new HashMap<>();

    /**
     * 加载properties文件中配置的请求名与类全名的映射
     */
    void loadProperties() {
        try {
            Properties properties = new Properties();
            properties.load(Thread.currentThread().getContextClassLoader().getResourceAsStream("MyMVC.properties"));

            Enumeration<?> names = properties.propertyNames();
            while (names.hasMoreElements()) {
                String key = (String) names.nextElement();
                String value = properties.getProperty(key);

                requestClassName.put(key, value);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 将uri转换为类全名返回
     *
     * @param requestURI
     * @return
     */
    String parseURI(String requestURI) {
        String uri = requestURI.substring(requestURI.lastIndexOf("/") + 1, requestURI.lastIndexOf(".do"));
        return requestClassName.get(uri);
    }

    /**
     * 将类全名对应的对象实例返回
     *
     * @param requestName
     * @return
     * @throws ClassNotFoundException
     * @throws IllegalAccessException
     * @throws InstantiationException
     */
    Object getRequestClass(String requestName) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        Object obj = requestClass.get(requestName);
        if (obj == null) {
            synchronized (requestName) {
                Class clazz = Class.forName(requestName);
                Object temp = clazz.newInstance();
                obj = temp;
                requestClass.put(requestName, temp);

                //扫描方法
                Map<String, Method> methodMap = new HashMap<>();
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    methodMap.put(method.getName(), method);
                }
                objectMethodMap.put(obj, methodMap);
            }
        }

        return obj;
    }

    /**
     * 将类实例对象中指定方法名的方法返回
     *
     * @param obj
     * @param methodName
     * @return
     */
    Method getMethod(Object obj, String methodName) {
        //拿到实例对象的方法列表
        Map<String, Method> methods = objectMethodMap.get(obj);
        Method method = methods.get(methodName);
        return method;
    }

    Object[] injectionParameter(Method method, HttpServletRequest request, HttpServletResponse response) throws IllegalAccessException, InstantiationException, NoSuchMethodException, InvocationTargetException {
        Parameter[] parameters = method.getParameters();
        //没有参数就直接返回
        if (parameters == null || parameters.length == 0)
            return null;

        //有参数就要放到Object数组里返回
        Object[] objects = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            //单独处理每一个参数
            Parameter parameter = parameters[i];
            //获取参数类型
            Class parameterType = parameter.getType();
            //判断该参数是否有RequestParam注解，有则说明是普通类型
            RequestParam paramAnnotation = parameter.getAnnotation(RequestParam.class);

            if (paramAnnotation != null) {
                objects[i] = injectionNormal(parameterType, paramAnnotation, request);
            } else {
                if (parameterType == Map.class || parameterType == List.class || parameterType == Set.class) {
                    //处理不了，抛出异常
                } else {
                    //如果用户就要自己用request或者response
                    if (parameterType == HttpServletRequest.class) {
                        objects[i] = request;
                        continue;
                    } else if (parameterType == HttpServletResponse.class) {
                        objects[i] = response;
                        continue;
                    }
                    //如果不是上面的问题，那么说明是Map的子类或者是domain类型
                    Object obj = parameterType.newInstance();
                    if (obj instanceof Map) {
                        objects[i] = injectionMap(obj, request);
                    } else {//domain类型
                        objects[i] = injectionDoMain(obj, parameterType,request);
                    }
                }
            }
        }

        return objects;
    }

    /**
     * 如果要注入的参数是普通类型的
     *
     * @param parameterType
     * @param paramAnnotation
     * @param request
     * @return
     */
    Object injectionNormal(Class parameterType, RequestParam paramAnnotation, HttpServletRequest request) {
        String key = paramAnnotation.value();
        String value = request.getParameter(key);

        Object result = null;
        if (parameterType == String.class) {
            result = value;
        } else if (parameterType == int.class || parameterType == Integer.class) {
            result = new Integer(value);
        } else if (parameterType == double.class || parameterType == Double.class) {
            result = new Double(value);
        } else if (parameterType == float.class || parameterType == Float.class) {
            result = new Float(value);
        } else if (parameterType == boolean.class || parameterType == Boolean.class) {
            result = new Boolean(value);
        } else {
            //处理不了，应该抛出异常
        }

        return result;
    }

    /**
     * 如果要注入的参数是map类型的
     *
     * @param obj
     * @param request
     * @return
     */
    Map injectionMap(Object obj, HttpServletRequest request) {
        Map map = (Map) obj;

        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String key = parameterNames.nextElement();
            String value = request.getParameter(key);
            map.put(key, value);
        }

        return map;
    }

    /**
     * 如果注入的是domain类型的参数
     * @param obj
     * @param parameterType
     * @param request
     * @return
     * @throws NoSuchMethodException
     * @throws IllegalAccessException
     * @throws InvocationTargetException
     * @throws InstantiationException
     */
    Object injectionDoMain(Object obj, Class parameterType, HttpServletRequest request) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        Field[] fields = parameterType.getDeclaredFields();

        for (Field field : fields) {
            field.setAccessible(true);
            String fieldName = field.getName();
            String value = request.getParameter(fieldName);

            Class fieldType = field.getType();
            Constructor con = fieldType.getConstructor(String.class);
            field.set(obj,con.newInstance(value));
        }

        return obj;
    }

    void finalResponse(Method method ,Object result,HttpServletRequest request,HttpServletResponse response) throws IOException, ServletException {
        //只有返回值不为空的时候才需要框架处理
        if(result!=null){
            //返回的是自定义类型
            if(result instanceof ModelAndView){
                ModelAndView mv = (ModelAndView)result;
                parseModelAndView(mv,request);
                parseResponseString(mv.getViewName(),request,response);
            }else if(result instanceof String){//返回的是字符串，如果有注解是json，如果没有就是响应
                ResponseBody body = method.getAnnotation(ResponseBody.class);
                if(body!=null) {//有注解，为json，直接返回
                    response.setContentType("text/html;charset=UTF-8");
                    response.getWriter().write((String) result);
                }else{
                    //没有注解，响应
                    String viewName = (String)result;
                    parseResponseString(viewName,request,response);
                }
            }else{
                //以上都不是，那么就应该是要转化为json
                ResponseBody responseBody = method.getAnnotation(ResponseBody.class);
                if(responseBody!=null){
                    JSONObject jsonObject = new JSONObject();
                    jsonObject.put("jsonObject",result);
                    response.getWriter().write(jsonObject.toJSONString());
                }
            }
        }
    }

    private void parseModelAndView(ModelAndView mv,HttpServletRequest request){
        Map<String, Object> map = mv.getAttributeMap();
        Iterator<String> iterator = map.keySet().iterator();
        while (iterator.hasNext()){
            String key = iterator.next();
            Object value = map.get(key);
            request.setAttribute(key,value);
        }
    }

    private void parseResponseString(String str,HttpServletRequest request,HttpServletResponse response) throws ServletException, IOException {
        //传入有问题就抛异常
        if(str==null||"".equals(str)){

            return;
        }

        String[] values = str.split(":");
        //没有冒号就是转发，values也就只有一个结果
        if(values.length==1){
            request.getRequestDispatcher(values[0]).forward(request,response);
        }else{//重定向
            if("redirect".equals(values[0])){
                response.sendRedirect(str.substring(str.indexOf(":")+1));
            }
        }
    }
}
