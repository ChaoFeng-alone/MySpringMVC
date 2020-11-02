package MyMVC;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class DispatcherServlet extends HttpServlet {
    private Handler handler = new Handler();
    @Override
    public void init(ServletConfig config) throws ServletException {
        handler.loadProperties();
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {

            //从request中拿到uri解析出类名
            String requestURI = request.getRequestURI();
            String uri = handler.parseURI(requestURI);

            //拿到类的实例对象
            Object obj = handler.getRequestClass(uri);

            //从request中拿出要执行的方法
            String methodName = request.getParameter("method");
            Method method = handler.getMethod(obj, methodName);

            //参数注入
            Object[] parameters = handler.injectionParameter(method,request,response);

            //调用方法，拿回返回值
            Object result = method.invoke(obj,parameters);

            handler.finalResponse(method,result,request,response);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }

    }
}
