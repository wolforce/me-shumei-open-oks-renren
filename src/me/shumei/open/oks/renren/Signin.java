package me.shumei.open.oks.renren;

import java.io.IOException;
import java.util.HashMap;

import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";
    
    String loginUrl = "http://mt.renren.com/login";
    String profileUrl = "http://3g.renren.com/profile.do";
    String captchaUrl = null;
    
    HashMap<String, String> cookies = new HashMap<String, String>();
    HashMap<String, String> postDatas = new HashMap<String, String>();
    Response res;
    Document doc;
    
    
    /**
     * <p><b>程序的签到入口</b></p>
     * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
     * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg “配置”栏内输入的数据
     * @param user 用户名
     * @param pwd 解密后的明文密码
     * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        //把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        //标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;
        
        try{
            //访问人人网登录页面
            res = Jsoup.connect(loginUrl).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            String _rtk = res.parse().getElementsByAttributeValue("name", "_rtk").val();
            
            //设置登录需要提交的数据
            postDatas.put("account", user);
            postDatas.put("password", pwd);
            postDatas.put("redirect", "http://mt.renren.com/profile");
            postDatas.put("_rtk", _rtk);
            postDatas.put("submit", "");
            
            
            //登录人人网账号
            res = submitLogin();
            
            //登录后出现验证码，需要二次验证才能登录（这种情况很少见）
            if(res.body().contains("验证码")) {
                doc = res.parse();
                String verifykey = doc.getElementsByAttributeValue("name", "verifykey").val();
                captchaUrl = doc.getElementById("verify_pic").attr("src");
                
                //使用验证码操作类中的showCaptcha方法获取、显示验证码
                if(CaptchaUtil.showCaptcha(captchaUrl , UA_ANDROID, cookies, "人人网", user, "登录需要验证码"))
                {
                    if (CaptchaUtil.captcha_input.length() > 0) {
                        //获取验证码成功，可以用CaptchaUtil.captcha_input继续做其他事了
                        //把验证码追加到要POST的数据中
                        postDatas.put("verifykey", verifykey);
                        postDatas.put("verifycode", CaptchaUtil.captcha_input);
                        //再次提交登录信息
                        res = submitLogin();
                    } else {
                        //用户取消输入验证码
                        this.resultFlag = "false";
                        this.resultStr = "用户放弃输入验证码，登录失败";
                        return new String[]{this.resultFlag,this.resultStr};
                    }
                } else {
                    //拉取验证码失败，签到失败
                    this.resultFlag = "false";
                    this.resultStr = "拉取验证码失败，无法登录";
                    return new String[]{this.resultFlag,this.resultStr};
                }
            }
            
            //不管是否登录成功，都跳转到用户信息界面，如果能获取到“连续登录”信息，则说明登录成功了，否则直接判定为登录失败
            res = Jsoup.connect(profileUrl).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.GET).execute();
            cookies.putAll(res.cookies());
            if(res.body().contains("连续登录")) {
                String continueDays = analyseContinueDays(res);
                this.resultFlag = "true";
                this.resultStr = "登录成功" + "，" + continueDays;
            } else {
                this.resultFlag = "false";
                this.resultStr = "登录失败，请检查账号密码是否正确";
            }
            
        } catch (IOException e) {
            this.resultFlag = "false";
            this.resultStr = "连接超时";
            e.printStackTrace();
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }
        
        return new String[]{resultFlag, resultStr};
    }
    
    
    /**
     * 提交登录信息
     * @return
     * @throws IOException
     */
    private Response submitLogin() throws IOException
    {
        //登录人人网账号
        Response tempRes;
        tempRes = Jsoup.connect(loginUrl).data(postDatas).cookies(cookies).userAgent(UA_ANDROID).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
        cookies.putAll(tempRes.cookies());
        return tempRes;
    }
    
    
    /**
     * 分析登录天数
     * @return 
     */
    private String analyseContinueDays(Response tempRes)
    {
        String continueDays = "";
        try {
            continueDays = tempRes.parse().select("div.ssec > p").text();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return continueDays;
    }
    
    
}
