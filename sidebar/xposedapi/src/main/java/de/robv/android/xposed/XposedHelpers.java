package de.robv.android.xposed;
public final class XposedHelpers {
  private XposedHelpers(){}
  public static XC_MethodHook.Unhook findAndHookMethod(Class<?> cls,String method,Object... args) {return null;}
  public static Class<?> findClassIfExists(String name,ClassLoader loader) {return null;}
  public static Class<?> findClass(String name,ClassLoader loader) {return null;}
  public static Object callMethod(Object receiver,String methodName,Object... args){return null;}
  public static Object callStaticMethod(Class<?> receiver,String methodName,Object... args){return null;}
}
