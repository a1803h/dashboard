package de.robv.android.xposed;
import java.lang.reflect.Member;
/** Compile-time signature only. Never packaged into application. */
public abstract class XC_MethodHook {
  protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
  protected void afterHookedMethod(MethodHookParam param) throws Throwable {}
  public static class MethodHookParam {
    public Member method;
    public Object thisObject;
    public Object[] args;
    public void setResult(Object result) {}
  }
  public class Unhook {}
}
