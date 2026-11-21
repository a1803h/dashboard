package de.robv.android.xposed;
import java.util.Set;
public final class XposedBridge {
  private XposedBridge(){}
  public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> cls,String methodName,XC_MethodHook hook){return null;}
  public static void log(String message) {}
}
