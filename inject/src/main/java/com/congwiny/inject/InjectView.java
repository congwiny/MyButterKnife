package com.congwiny.inject;

import android.app.Activity;

/**
 * Created by congwiny on 2017/8/17.
 */

public class InjectView {
    public static void bind(Activity activity){
        String className = activity.getClass().getName();
        try {
            Class<?> viewBinderClass = Class.forName(className+"$$ViewBinder");
            ViewBinder<Activity> viewBinder = (ViewBinder<Activity>) viewBinderClass.newInstance();
            viewBinder.bind(activity);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
