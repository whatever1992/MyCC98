package tk.djcrazy.libCC98;

import android.app.Application;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import com.android.volley.toolbox.ImageRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ch.boye.httpclientandroidlib.cookie.Cookie;
import ch.boye.httpclientandroidlib.impl.cookie.BasicClientCookie;
import ch.boye.httpclientandroidlib.impl.cookie.BasicClientCookie2;
import tk.djcrazy.MyCC98.Config;
import tk.djcrazy.MyCC98.HomeActivity;
import tk.djcrazy.MyCC98.R;
import tk.djcrazy.MyCC98.application.MyApplication;
import tk.djcrazy.MyCC98.bean.UpdateInfo;
import tk.djcrazy.libCC98.data.HotTopicEntity;
import tk.djcrazy.libCC98.data.LoginType;
import tk.djcrazy.libCC98.data.UserData;
import tk.djcrazy.libCC98.data.UserProfileEntity;
import tk.djcrazy.libCC98.util.ParamMapBuilder;
import tk.djcrazy.libCC98.util.RequestResultListener;

/**
 * Created by DJ on 13-7-28.
 */
@Singleton
public class NewCC98Service {


    @Inject
    private ICC98UrlManager mUrlManager;
    @Inject
    private Application mApplication;
    @Inject
    private NewCC98Parser mCC98Parser;


    public void submitHotTopicRequest(Object tag, final RequestResultListener<List<HotTopicEntity>> listRequestResultListener) {
        Request request = new StringRequest(mUrlManager.getHotTopicUrl(),new Response.Listener<String>() {
            @Override
            public void onResponse(String s) {
                //do some parser job
                listRequestResultListener.onReuqestComplete(null);
            }
        },new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                listRequestResultListener.onReuqestError(volleyError.getLocalizedMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    public void submitUpdateRequest(Object tag, final RequestResultListener<JSONObject> listener) {
        Request request = new StringRequest(Request.Method.GET, Config.UPDATE_LINK,new Response.Listener<String>() {
            @Override
            public void onResponse(String result) {
                try {
                    listener.onReuqestComplete(new JSONObject(result));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        },new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError volleyError) {
                listener.onReuqestError(volleyError.getLocalizedMessage());
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    public void login(final Object tag, final String userName, final String pwd32,  final String pwd16, String proxyName,
                      String proxyPwd, String proxyHost, LoginType type ,final RequestResultListener<Boolean> listener) {
        Log.d(this.getClass().getSimpleName(), userName+pwd32+proxyName+proxyPwd+proxyHost+type);
        final UserData userData = new UserData();
        userData.setProxyUserName(proxyName);
        userData.setProxyHost(proxyHost);
        userData.setProxyPassword(proxyPwd);
        userData.setLoginType(type);
        userData.setUserName(userName);
        userData.setPassword16(pwd16);
        userData.setPassword32(pwd32);
        StringRequest request = new StringRequest(Request.Method.POST, mUrlManager.getLoginUrl(type, proxyHost),new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.contains("9898")){
                    getUserAvatar1(tag, userData,listener);
                 } else {
                    listener.onReuqestError("用户名或密码错误");
                    getApplication().syncUserDataAndHttpClient();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onReuqestError(error.getLocalizedMessage());
                getApplication().syncUserDataAndHttpClient();
            }
        }) {
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                return  new ParamMapBuilder()
                        .param("a", "i").param("u", userName).param("p", pwd32).param("userhidden", "2").buildMap();
            }
        };
        getApplication().syncUserDataAndHttpClient(userData);
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }


    private void getUserAvatar1(final Object tag, final UserData userData, final RequestResultListener<Boolean> listener) {
        Request request = new StringRequest(mUrlManager.getUserProfileUrl(
                userData.getLoginType(),userData.getProxyHost(), userData.getUserName()), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                try {
                    userData.setCookies(castToAnother(getApplication().mHttpClient.getCookieStore().getCookies()));
                    UserProfileEntity entity =  mCC98Parser.parseUserProfile(response);
                    System.out.println(entity);
                    getUserAvatar2(tag, userData, entity.getUserAvatarLink(), listener);
                } catch (Exception e) {
                    e.printStackTrace();
                    getApplication().syncUserDataAndHttpClient();
                    listener.onReuqestError("解析头像地址失败，请重试");
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                listener.onReuqestError("获取头像地址失败，请重试");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    private void getUserAvatar2(final Object tag, final UserData userData, String url, final RequestResultListener<Boolean> listener){
        Request request = new ImageRequest(url, new Response.Listener<Bitmap>() {
            @Override
            public void onResponse(Bitmap response) {
                getApplication().addNewUser(userData, response, true);
                listener.onReuqestComplete(true);
            }
        }, 200, 200, Bitmap.Config.ARGB_8888, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                getApplication().syncUserDataAndHttpClient();
                listener.onReuqestError("下载头像失败，请重试");
            }
        });
        request.setTag(tag);
        getApplication().mRequestQueue.add(request);
    }

    private List<BasicClientCookie> castToAnother(List<Cookie> list) {
        List<BasicClientCookie> res = new ArrayList<BasicClientCookie>();
        for (Cookie cookie: list) {
            res.add((BasicClientCookie)cookie);
        }
        return res;
    }

    public void cancelRequest(Object object) {
        getApplication().mRequestQueue.cancelAll(object);
    }

    public ImageLoader getImageLoader() {
        return getApplication().mImageLoader;
    }

     private MyApplication getApplication() {
        return (MyApplication) mApplication;
    }
}
