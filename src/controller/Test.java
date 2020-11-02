package controller;

import MyMVC.ModelAndView;
import MyMVC.RequestParam;
import domain.User;

public class Test {
    public ModelAndView dotest(User user){
        System.out.println(user);
        ModelAndView mv = new ModelAndView();
        mv.setViewName("index.jsp");
        mv.addAttribute("user",user);
        return mv;
    }

}
