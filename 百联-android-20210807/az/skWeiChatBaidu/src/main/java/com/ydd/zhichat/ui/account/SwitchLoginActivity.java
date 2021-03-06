package com.ydd.zhichat.ui.account;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.alibaba.fastjson.JSON;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.animation.GlideAnimation;
import com.bumptech.glide.request.target.SimpleTarget;
import com.ydd.zhichat.AppConstant;
import com.ydd.zhichat.MyApplication;
import com.ydd.zhichat.R;
import com.ydd.zhichat.bean.Code;
import com.ydd.zhichat.bean.LoginRegisterResult;
import com.ydd.zhichat.bean.User;
import com.ydd.zhichat.bean.WXUploadResult;
import com.ydd.zhichat.db.InternationalizationHelper;
import com.ydd.zhichat.helper.DialogHelper;
import com.ydd.zhichat.helper.LoginHelper;
import com.ydd.zhichat.helper.PrivacySettingHelper;
import com.ydd.zhichat.helper.UsernameHelper;
import com.ydd.zhichat.ui.MainActivity;
import com.ydd.zhichat.ui.base.BaseActivity;
import com.ydd.zhichat.util.Constants;
import com.ydd.zhichat.util.DeviceInfoUtil;
import com.ydd.zhichat.util.Md5Util;
import com.ydd.zhichat.util.PreferenceUtils;
import com.ydd.zhichat.util.ToastUtil;
import com.ydd.zhichat.volley.Result;
import com.ydd.zhichat.wxapi.WXHelper;
import com.xuan.xuanhttplibrary.okhttp.HttpUtils;
import com.xuan.xuanhttplibrary.okhttp.callback.BaseCallback;
import com.xuan.xuanhttplibrary.okhttp.callback.JsonCallback;
import com.xuan.xuanhttplibrary.okhttp.result.ObjectResult;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import okhttp3.Call;

public class SwitchLoginActivity extends BaseActivity implements View.OnClickListener {
    private EditText auth_code_edit;
    private Button loginBtn;
    private User mLastLoginUser;
    private TextView tv_prefix;
    private int mobilePrefix = 86;
    private String mobileCountry = "??????";
    private int mOldLoginStatus;
    private Button mSendAgainBtn;
    private int reckonTime = 60;
    private String mRandCode;
    private EditText mImageCodeEdit;
    private ImageView mImageCodeIv;
    private ImageView mRefreshIv;
    private TextView mNickNameTv;
    private ImageView mAvatarImgView;
    private LinearLayout lin_select;
    private String mImageCodeStr;
    private EditText mPhoneNumberEdit;
    private String thirdToken;

    private Handler mReckonHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == 0x1) {
                mSendAgainBtn.setText(reckonTime + " " + "S");
                reckonTime--;
                if (reckonTime < 0) {
                    mReckonHandler.sendEmptyMessage(0x2);
                } else {
                    mReckonHandler.sendEmptyMessageDelayed(0x1, 1000);
                }
            } else if (msg.what == 0x2) {
                // 60?????????
                mSendAgainBtn.setText(InternationalizationHelper.getString("JX_Send"));
                mSendAgainBtn.setEnabled(true);
                reckonTime = 60;
            }
        }
    };
    private String phone;

    public SwitchLoginActivity() {
        noLoginRequired();
    }

    public static void bindThird(Context ctx, WXUploadResult thirdToken) {
        Intent intent = new Intent(ctx, SwitchLoginActivity.class);
        intent.putExtra("thirdTokenLogin", JSON.toJSONString(thirdToken));
        ctx.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_switch_new);
        PreferenceUtils.putBoolean(this, Constants.LOGIN_CONFLICT, false);// ????????????????????????

        mOldLoginStatus = MyApplication.getInstance().mUserStatus;
        thirdToken = getIntent().getStringExtra("thirdTokenLogin");
        initActionBar();
        initView();
        if (!TextUtils.isEmpty(thirdToken)) {
            // ??????????????????????????????
            // ?????????????????????????????????????????????
            mPhoneNumberEdit.setText("");
            login(true);
        }
    }

    private void initActionBar() {
        getSupportActionBar().hide();
        findViewById(R.id.iv_title_left).setOnClickListener(v -> finish());
        TextView tvTitle = (TextView) findViewById(R.id.tv_title_center);
        tvTitle.setVisibility(View.GONE);
        tvTitle.setText(getString(R.string.verification_code) + getString(R.string.login));
        TextView tvRight = (TextView) findViewById(R.id.tv_title_right);
        tvRight.setVisibility(View.GONE);
    }

    private void initView() {
        mPhoneNumberEdit = findViewById(R.id.phone_numer_edit);
        tv_prefix = (TextView) findViewById(R.id.tv_prefix);
        lin_select = findViewById(R.id.lin_select);
//        if (coreManager.getConfig().registerUsername) {
//            tv_prefix.setVisibility(View.GONE);
//            lin_select.setVisibility(View.GONE);
//        } else {
            tv_prefix.setOnClickListener(this);
            lin_select.setOnClickListener(this);
//        }
        mImageCodeEdit = (EditText) findViewById(R.id.image_tv);
        mImageCodeIv = (ImageView) findViewById(R.id.image_iv);
        mRefreshIv = (ImageView) findViewById(R.id.image_iv_refresh);
        mSendAgainBtn = (Button) findViewById(R.id.send_again_btn);
        auth_code_edit = findViewById(R.id.auth_code_edit);
        loginBtn = (Button) findViewById(R.id.login_btn);
        loginBtn.setOnClickListener(this);

        UsernameHelper.initEditText(mPhoneNumberEdit, coreManager.getConfig().registerUsername);

        findViewById(R.id.main_content).setOnClickListener(this);

        tv_prefix.setText(mobileCountry+"(+" + mobilePrefix+")");

        mRefreshIv.setOnClickListener(new View.OnClickListener() {// ???????????????
            @Override
            public void onClick(View v) {
                try {
                    if (TextUtils.isEmpty(mPhoneNumberEdit.getText().toString().trim())) {
                        Toast.makeText(mContext, "???????????????,???????????????????????????", Toast.LENGTH_SHORT).show();
                    } else {
                        requestImageCode();
                    }
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        mSendAgainBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                phone = mPhoneNumberEdit.getText().toString().trim();
                mImageCodeStr = mImageCodeEdit.getText().toString().trim();
                if (TextUtils.isEmpty(mImageCodeStr)) {
                    ToastUtil.showToast(mContext, getString(R.string.tip_verification_code_empty));
                    return;
                }
                // ???????????????????????????
                verifyPhoneIsRegistered(phone, mImageCodeStr);

            }
        });

        mPhoneNumberEdit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (!hasFocus) {
                    // ????????????????????????????????????????????????????????????
                    // ??????????????????????????????????????????EditText????????????
                    requestImageCode();
                }
            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.lin_select:
                // ??????????????????
                Intent intent = new Intent(this, SelectPrefixActivity.class);
                startActivityForResult(intent, SelectPrefixActivity.REQUEST_MOBILE_PREFIX_LOGIN);
                break;
            case R.id.tv_prefix:
                // ??????????????????
//                intent = new Intent(this, SelectPrefixActivity.class);
//                startActivityForResult(intent, SelectPrefixActivity.REQUEST_MOBILE_PREFIX_LOGIN);
                break;
            case R.id.login_btn:
                if (TextUtils.isEmpty(phone)) {
                    Toast.makeText(mContext, "?????????????????????", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (TextUtils.isEmpty(mRandCode)) {
                    Toast.makeText(mContext, "?????????????????????", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (mRandCode.equals(auth_code_edit.getText().toString().trim())) {
                    Log.e("zx", "onClick: " + "login_btn");
                    login(false);
                } else {
                    Toast.makeText(SwitchLoginActivity.this, "???????????????", Toast.LENGTH_SHORT).show();
                }
                break;
            case R.id.register_account_btn:
                startActivity(new Intent(SwitchLoginActivity.this, RegisterActivity.class));
                break;
            case R.id.forget_password_btn:
                Intent intentToFind = new Intent(mContext, FindPwdActivity.class);
                intentToFind.putExtra("country",mobileCountry);
                startActivity(intentToFind);
                break;
            case R.id.switch_account_btn:
                finish();
                break;
            case R.id.main_content:
                // ?????????????????????????????????
                InputMethodManager inputManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (inputManager != null) {
                    inputManager.hideSoftInputFromWindow(findViewById(R.id.main_content).getWindowToken(), 0); //??????????????????
                }
                break;
        }
    }

    /**
     * ???????????????
     */
    private void requestAuthCode(String phoneStr, String imageCodeStr) {
        Map<String, String> params = new HashMap<>();
        String language = Locale.getDefault().getLanguage();
        params.put("language", language);
        params.put("areaCode", String.valueOf(mobilePrefix));
        params.put("telephone", phoneStr);
        Log.e("zx", "requestAuthCode: " + phoneStr);
        params.put("imgCode", imageCodeStr);
        params.put("isRegister", String.valueOf(0));
        params.put("version", "1");

        DialogHelper.showDefaulteMessageProgressDialog(this);
        Log.e("zx", "requestAuthCode: " + imageCodeStr);
        HttpUtils.get().url(coreManager.getConfig().SEND_AUTH_CODE)
                .params(params)
                .build()
                .execute(new BaseCallback<Code>(Code.class) {

                    @Override
                    public void onResponse(ObjectResult<Code> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result.getResultCode() == 1) {
                            Log.e("zx", "onResponse: " + result.getData().getCode());
                            mSendAgainBtn.setEnabled(false);
                            mRandCode = result.getData().getCode();// ???????????????
                            // ???????????????
                            mReckonHandler.sendEmptyMessage(0x1);
                        } else {
                            if (!TextUtils.isEmpty(result.getResultMsg())) {
                                ToastUtil.showToast(SwitchLoginActivity.this,
                                        result.getResultMsg());
                            } else {
                                ToastUtil.showToast(SwitchLoginActivity.this,
                                        getString(R.string.tip_server_error));
                            }
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showNetError(mContext);
                    }
                });
    }

    /**
     * ????????????????????????
     */
    private void verifyPhoneIsRegistered(final String phoneStr, final String imageCodeStr) {
        verifyPhoneNumber(phoneStr, new Runnable() {
            @Override
            public void run() {
                requestAuthCode(phoneStr, imageCodeStr);
            }
        });
    }

    /**
     * ?????????????????????
     */
    private void requestImageCode() {
        Map<String, String> params = new HashMap<>();
        params.put("telephone", mobilePrefix + mPhoneNumberEdit.getText().toString().trim());
        HttpUtils.get().url(coreManager.getConfig().USER_GETCODE_IMAGE)
                .params(params)
                .build().execute(new JsonCallback() {
            @Override
            public void onResponse(String result) {
                try {
                    String url = coreManager.getConfig().USER_GETCODE_IMAGE + "?telephone=" + mobilePrefix + mPhoneNumberEdit.getText().toString().trim();
                    Glide.with(mContext).load(url)
                            .asBitmap()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true)
                            .into(new SimpleTarget<Bitmap>() {
                                @Override
                                public void onResourceReady(Bitmap bitmap, GlideAnimation anim) {
                                    if(mImageCodeIv!=null) {
                                        mImageCodeIv.setImageBitmap(bitmap);
                                    }
                                }

                                @Override
                                public void onLoadFailed(Exception e, Drawable errorDrawable) {
                                    Toast.makeText(SwitchLoginActivity.this, R.string.tip_verification_code_load_failed, Toast.LENGTH_SHORT).show();
                                }
                            });
                }catch (Exception e){
                    e.printStackTrace();
                }
            }

            @Override
            public void onError(Call call, Exception e) {

            }
        });
    }

    private void verifyPhoneNumber(String phoneNumber, final Runnable onSuccess) {
        Map<String, String> params = new HashMap<>();
        params.put("telephone", phoneNumber);
        params.put("areaCode", "" + mobilePrefix);
        params.put("verifyType", "1");
        HttpUtils.get().url(coreManager.getConfig().VERIFY_TELEPHONE)
                .params(params)
                .build()
                .execute(new BaseCallback<Void>(Void.class) {

                    @Override
                    public void onResponse(ObjectResult<Void> result) {
                        DialogHelper.dismissProgressDialog();
                        if (result == null) {
                            ToastUtil.showToast(SwitchLoginActivity.this,
                                    R.string.data_exception);
                            return;
                        }

                        if (result.getResultCode() == 1) {
                            onSuccess.run();
                        } else {
                            requestImageCode();
                            // ????????????????????????
                            if (!TextUtils.isEmpty(result.getResultMsg())) {
                                ToastUtil.showToast(SwitchLoginActivity.this,
                                        result.getResultMsg());
                            } else {
                                ToastUtil.showToast(SwitchLoginActivity.this,
                                        R.string.tip_server_error);
                            }
                        }
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(SwitchLoginActivity.this);
                    }
                });
    }

    private void login(boolean third) {
        PreferenceUtils.putInt(this, Constants.AREA_CODE_KEY, mobilePrefix);
        PreferenceUtils.putString(this, Constants.COUNTRY_NANE, mobileCountry);
        if (TextUtils.isEmpty(thirdToken)) {
            // ??????????????????????????????????????????
            if (TextUtils.isEmpty(phone)) {
                Toast.makeText(mContext, getString(R.string.please_input_account), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (TextUtils.isEmpty(mRandCode)) {
            return;
        }
        final String phoneNumber = mPhoneNumberEdit.getText().toString().trim();
//        final String digestPwd = new String(Md5Util.toMD5(code));
        DialogHelper.showDefaulteMessageProgressDialog(this);
        HashMap<String, String> params = new HashMap<>();
        // ????????????8618720966659,??????????????????18720966659

        // ????????????,
        String sPrefix = String.valueOf(mobilePrefix);
        String phoneNumberRel;
        if (phoneNumber.startsWith(sPrefix)) {
            phoneNumberRel = phoneNumber.substring(sPrefix.length());
        } else {
            phoneNumberRel = phoneNumber;
        }
        params.put("telephone", Md5Util.toMD5(phoneNumberRel));// ???????????????????????????MD5????????????????????????
        params.put("verificationCode", mRandCode);
        //params.put("imgCode", mImageCodeStr);
        Log.e("zx", "login: " + mRandCode);
        params.put("xmppVersion", "1");
        params.put("areaCode", String.valueOf(mobilePrefix));
        // ????????????
        params.put("model", DeviceInfoUtil.getModel());
        params.put("osVersion", DeviceInfoUtil.getOsVersion());
        params.put("serial", DeviceInfoUtil.getDeviceId(mContext));
        params.put("loginType", "1");//???????????????

        // ????????????
        double latitude = MyApplication.getInstance().getBdLocationHelper().getLatitude();
        double longitude = MyApplication.getInstance().getBdLocationHelper().getLongitude();
        if (latitude != 0)
            params.put("latitude", String.valueOf(latitude));
        if (longitude != 0)
            params.put("longitude", String.valueOf(longitude));

        if (MyApplication.IS_OPEN_CLUSTER) {// ?????????????????????
            String area = PreferenceUtils.getString(this, AppConstant.EXTRA_CLUSTER_AREA);
            if (!TextUtils.isEmpty(area)) {
                params.put("area", area);
            }
        }
        String url;
        if (TextUtils.isEmpty(thirdToken)) {
            url = coreManager.getConfig().USER_LOGIN;
        } else {
            params.put("type", "2");
            params.put("loginInfo", WXHelper.parseOpenId(thirdToken));
            if (third) {
                // ????????????????????????
                // ???????????????????????????????????????
                // ????????????1040305??????????????????????????????IM?????????
                // ?????????????????????????????????????????????????????????
                url = coreManager.getConfig().USER_THIRD_LOGIN;
            } else {
                // ????????????IM?????????????????????IM????????????????????????
                // ????????????1040306????????????IM??????????????????????????????????????????????????????IM????????????????????????
                url = coreManager.getConfig().USER_THIRD_BIND;
                // ??????????????????????????????MD5???????????????????????????
                params.put("telephone", mobilePrefix + phoneNumber);
            }
        }
        HttpUtils.get().url(url)
                .params(params)
                .build()
                .execute(new BaseCallback<LoginRegisterResult>(LoginRegisterResult.class) {

                    @Override
                    public void onResponse(ObjectResult<LoginRegisterResult> result) {

                        if (result == null) {
                            DialogHelper.dismissProgressDialog();
                            ToastUtil.showErrorData(mContext);
                            return;
                        }
                        boolean success = false;
                        if (result.getResultCode() == Result.CODE_SUCCESS) {
                            success = LoginHelper.setLoginUser(mContext, coreManager, phone, result.getData().getPassword(), result);// ????????????????????????
                        }

                        if (success) {
                            LoginRegisterResult.Settings settings = result.getData().getSettings();
                            MyApplication.getInstance().initPayPassword(result.getData().getUserId(), result.getData().getPayPassword());
                            PrivacySettingHelper.setPrivacySettings(SwitchLoginActivity.this, settings);
                            MyApplication.getInstance().initMulti();
                            PreferenceUtils.putString(SwitchLoginActivity.this, Constants.COUNTRY_NANE, mobileCountry);
                            // ????????????
                            LoginRegisterResult.Login login = result.getData().getLogin();
                            if (login != null && login.getSerial() != null && login.getSerial().equals(DeviceInfoUtil.getDeviceId(mContext))
                                    && mOldLoginStatus != LoginHelper.STATUS_USER_NO_UPDATE && mOldLoginStatus != LoginHelper.STATUS_NO_USER) {
                                // ??????Token????????????????????????????????????????????????????????????Main??????
                                // ?????????????????????????????????DataDownloadActivity??????DataDownloadActivity??????????????????
                                LoginHelper.broadcastLogin(SwitchLoginActivity.this);
                                // Intent intent = new Intent(mContext, MainActivity.class);
                                Intent intent = new Intent(SwitchLoginActivity.this, MainActivity.class);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                startActivity(intent);
                            } else {
                                // ?????????????????????????????????
                                // startActivity(new Intent(SwitchLoginActivity.this, DataDownloadActivity.class));
                                DataDownloadActivity.start(mContext, result.getData().getIsupdate());
                            }
                            finish();
                        } else {
                            // ????????????
                            String message = TextUtils.isEmpty(result.getResultMsg()) ? getString(R.string.login_failed) : result.getResultMsg();
                            ToastUtil.showToast(mContext, message);
                        }
                        DialogHelper.dismissProgressDialog();
                    }

                    @Override
                    public void onError(Call call, Exception e) {
                        DialogHelper.dismissProgressDialog();
                        ToastUtil.showErrorNet(mContext);
                    }
                });
    }

    private void register() {
        RegisterActivity.registerFromThird(
                this,
                mobilePrefix,
                mobileCountry,
                mPhoneNumberEdit.getText().toString(),
                null
                , thirdToken
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != SelectPrefixActivity.RESULT_MOBILE_PREFIX_SUCCESS)
            return;
        mobilePrefix = data.getIntExtra(Constants.MOBILE_PREFIX, 86);
        mobileCountry = data.getStringExtra(Constants.MOBILE_COUNTRY);
        tv_prefix.setText(mobileCountry+"(+" + mobilePrefix+")");
    }
}
