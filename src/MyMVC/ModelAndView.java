package MyMVC;

import java.util.HashMap;
import java.util.Map;

public class ModelAndView {
    private String viewName;
    private Map<String,Object> attributeMap = new HashMap<>();

    public void setViewName(String viewName){
        this.viewName = viewName;
    }

    public void addAttribute(String key , Object value){
        this.attributeMap.put(key,value);
    }

    String getViewName(){
        return this.viewName;
    }
    Object getAttribute(String key){
        return this.attributeMap.get(key);
    }
    Map<String,Object> getAttributeMap(){
        return this.attributeMap;
    }
}
