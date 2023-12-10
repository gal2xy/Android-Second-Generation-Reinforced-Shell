package com.example.androidsecondshell2;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class RefInvoke {

    /**
     * 反射获取指定字段的值
     * @param className: 实例对应的类
     * @param field: 所要获取实例中的字段
     * @param instanceObj: 实例对象，获取静态变量时设置为null
     * @return
     */
    public static Object getFieldObject(String className, String field, Object instanceObj){

        try{
            Class<?> aClass = Class.forName(className);
            Field declaredField = aClass.getDeclaredField(field);
            declaredField.setAccessible(true);
            Object obj = declaredField.get(instanceObj);
            return obj;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

    }

    /**
     * 反射设置指定字段的值
     * @param className: 实例对应的类
     * @param field: 所要设置实例中的字段
     * @param instanceObj: 实例对象，设置静态变量时设置为null
     * @param fieldValue: 字段所要设置成的值
     * @return
     */
    public static void setFieldObject(String className, String field, Object instanceObj ,Object fieldValue){
        try{
            Class<?> aClass = Class.forName(className);
            Field declaredField = aClass.getDeclaredField(field);
            declaredField.setAccessible(true);
            declaredField.set(instanceObj, fieldValue);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反射调用指定方法
     * @param className: 想要调用的方法所在的类名
     * @param methodName: 所要调用方法的方法名
     * @param paramTypes: 所要调用方法的参数类型数组，外部可通过匿名数组传入new Class[]{paramTypes that the method need}
     * @param instanceObj: 类的实例对象，调用静态静态方法时设置为null
     * @param paramValues: 调用方法时所要传入的具体参数值，外部可通过匿名数组传入new Obj[]{paramValues that the method need}
     * @return
     */
    public static Object invokeMethod(String className, String methodName, Class[] paramTypes, Object instanceObj , Object[] paramValues){
        try{
            Class<?> aClass = Class.forName(className);
            Method declaredMethod = aClass.getDeclaredMethod(methodName, paramTypes);
            declaredMethod.setAccessible(true);
            Object obj = declaredMethod.invoke(instanceObj, paramValues);
            return obj;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 反射调用指定构造方法来创建对象
     * @param className:
     * @param paramTypes:
     * @param paramValues:
     * @return
     */
    public static Object creatObject(String className, Class[] paramTypes,Object[] paramValues){
        try {
            Class<?> aClass = Class.forName(className);
            Constructor<?> declaredConstructor = aClass.getDeclaredConstructor(paramTypes);
            declaredConstructor.setAccessible(true);
            Object obj = declaredConstructor.newInstance(paramValues);
            return obj;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

}