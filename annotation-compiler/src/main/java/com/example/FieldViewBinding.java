package com.example;

import javax.lang.model.type.TypeMirror;

/**
 * Created by congwiny on 2017/8/17.
 */

public class FieldViewBinding {

    private TypeMirror type; //TextView
    private String name; //myTextView
    private int resId; //R.id.text_view

    public FieldViewBinding(TypeMirror type, String name, int resId) {
        this.type = type;
        this.name = name;
        this.resId = resId;
    }

    public int getResId() {
        return resId;
    }

    public String getName() {
        return name;
    }

    public TypeMirror getType() {
        return type;
    }
}
