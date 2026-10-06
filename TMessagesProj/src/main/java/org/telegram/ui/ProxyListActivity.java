/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui;

import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.DynamicDrawableSpan;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.ProxyRotationController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.XraySubscriptionStore;
import org.telegram.messenger.XraySubscriptionWorkScheduler;
import org.telegram.messenger.browser.Browser;
import org.telegram.utils.proxy.ProxySettings;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import tw.nekomimi.nekogram.utils.NekoProxyUtil;
import tw.nekomimi.nekogram.utils.ProxyUtil;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BackDrawable;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LineProgressView;
import org.telegram.ui.Components.OutlineTextContainerView;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SlideChooseView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

public class ProxyListActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {
    private final static boolean IS_PROXY_ROTATION_AVAILABLE = true;
    private static final int MENU_DELETE = 0;
    private static final int MENU_SHARE = 1;

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    @SuppressWarnings("FieldCanBeLocal")
    private LinearLayoutManager layoutManager;

    private int currentConnectionState;

    private boolean useProxySettings;
    private boolean useProxyForCalls;

    private int rowCount;
    @Keep
    private int useProxyRow;
    private int useProxyShadowRow;
    private int connectionsHeaderRow;
    private int proxyStartRow;
    private int proxyEndRow;
    private int subscriptionHeaderRow;
    private int subscriptionStartRow;
    private int subscriptionEndRow;
    private int manualHeaderRow;
    private int manualStartRow;
    private int manualEndRow;
    @Keep
    private int proxyAddRow;
    private int proxyShadowRow;
    private int callsRow;
    private int callsDetailRow;
    private int rotationRow;
    private int rotationTimeoutRow;
    private int rotationTimeoutInfoRow;
    private int deleteAllRow;
    private int subscriptionUserAgentRow;

    private ItemTouchHelper itemTouchHelper;
    private NumberTextView selectedCountTextView;
    private ActionBarMenuItem shareMenuItem;
    private ActionBarMenuItem deleteMenuItem;
    private ActionBarMenuSubItem hwidModeItem;
    private final static int menu_add_input_telegram = 1001;
    private final static int menu_import_clipboard = 1002;
    private final static int menu_import_url = 1003;
    private final static int menu_retest_ping = 1004;
    private final static int menu_delete_all = 1005;
    private final static int menu_delete_unavailable = 1006;
    private final static int menu_refresh_subscriptions = 1007;
    private final static int menu_hwid_mode = 1008;
    private final static int menu_paste_clipboard = 1009;
    private final static int menu_add_proxy = 1010;

    private List<SharedConfig.ProxyInfo> selectedItems = new ArrayList<>();
    private List<SharedConfig.ProxyInfo> proxyList = new ArrayList<>();
    private List<SharedConfig.ProxyInfo> subscriptionProxyList = new ArrayList<>();
    private List<SharedConfig.ProxyInfo> manualProxyList = new ArrayList<>();
    private boolean wasCheckedAllList;
    private final List<SubscriptionGroup> subscriptionGroups = new ArrayList<>();
    private final List<SubscriptionRow> subscriptionRows = new ArrayList<>();
    private final HashSet<String> collapsedSubscriptions = new HashSet<>();
    private boolean canCollapseSubscriptions;
    private Runnable subscriptionMetaRefreshRunnable;
    private static final long SUBSCRIPTION_META_REFRESH_MS = 30_000;
    private static final String SORT_ADDED = "added";
    private static final String SORT_PING = "ping";
    private static final String SORT_NAME = "name";

    private static class SubscriptionGroup {
        final String name;
        String subscriptionUrl = "";
        final ArrayList<SharedConfig.ProxyInfo> proxies = new ArrayList<>();

        SubscriptionGroup(String name) {
            this.name = name;
        }
    }

    private static class SubscriptionRow {
        final SubscriptionGroup group;
        final SharedConfig.ProxyInfo proxy;
        final boolean isGroup;

        private SubscriptionRow(SubscriptionGroup group, SharedConfig.ProxyInfo proxy, boolean isGroup) {
            this.group = group;
            this.proxy = proxy;
            this.isGroup = isGroup;
        }

        static SubscriptionRow forGroup(SubscriptionGroup group) {
            return new SubscriptionRow(group, null, true);
        }

        static SubscriptionRow forProxy(SharedConfig.ProxyInfo proxy) {
            return new SubscriptionRow(null, proxy, false);
        }
    }

    public class TextDetailProxyCell extends FrameLayout {

        private TextView textView;
        private TextView valueTextView;
        private ImageView checkImageView;
        private SharedConfig.ProxyInfo currentInfo;
        private Drawable checkDrawable;

        private CheckBox2 checkBox;
        private boolean isSelected;
        private boolean isSelectionEnabled;

        private int color;

        public TextDetailProxyCell(Context context) {
            super(context);

            textView = new TextView(context);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            textView.setLines(1);
            textView.setMaxLines(1);
            textView.setSingleLine(true);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 10, (LocaleController.isRTL ? 21 : 56), 0));

            valueTextView = new TextView(context);
            valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
            valueTextView.setLines(1);
            valueTextView.setMaxLines(1);
            valueTextView.setSingleLine(true);
            valueTextView.setCompoundDrawablePadding(AndroidUtilities.dp(6));
            valueTextView.setEllipsize(TextUtils.TruncateAt.END);
            valueTextView.setPadding(0, 0, 0, 0);
            addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 56 : 21), 35, (LocaleController.isRTL ? 21 : 56), 0));

            checkImageView = new ImageView(context);
            checkImageView.setImageResource(R.drawable.msg_info);
            checkImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText3), PorterDuff.Mode.MULTIPLY));
            checkImageView.setScaleType(ImageView.ScaleType.CENTER);
            checkImageView.setContentDescription(getString(R.string.Edit));
            addView(checkImageView, LayoutHelper.createFrame(48, 48, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 8, 8, 8, 0));
            checkBox = new CheckBox2(context, 21);
            checkBox.setColor(Theme.key_checkbox, Theme.key_radioBackground, Theme.key_checkboxCheck);
            checkBox.setDrawBackgroundAsArc(14);
            checkBox.setVisibility(GONE);
            addView(checkBox, LayoutHelper.createFrame(24, 24, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL, 16, 0, 8, 0));

            setWillNotDraw(false);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(64) + 1, MeasureSpec.EXACTLY));
        }

        public void setProxy(SharedConfig.ProxyInfo proxyInfo) {
            currentInfo = proxyInfo;
            String title;
            if (proxyInfo.unrecognized) {
                title = !TextUtils.isEmpty(proxyInfo.proxyName) ? proxyInfo.proxyName : getString(R.string.ProxyUnknownType);
                textView.setText(title);
                valueTextView.setText(getString(R.string.ProxyUnrecognizable));
                valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                checkImageView.setOnClickListener(v -> Toast.makeText(getContext(), getString(R.string.ProxyUnrecognizedToast), Toast.LENGTH_SHORT).show());
                return;
            }
            if (proxyInfo.isXrayVless()) {
                title = !TextUtils.isEmpty(proxyInfo.vlessRemark) ? proxyInfo.vlessRemark : proxyInfo.settings.getAddress() + ":" + proxyInfo.settings.getPort();
            } else if (proxyInfo.isAether()) {
                title = !TextUtils.isEmpty(proxyInfo.proxyName) ? proxyInfo.proxyName : "Aether";
            } else if (!TextUtils.isEmpty(proxyInfo.proxyName)) {
                title = proxyInfo.proxyName;
            } else {
                title = proxyInfo.settings.getAddress() + (proxyInfo.settings.getType() == ProxySettings.Type.WEB ? "" : ":" + proxyInfo.settings.getPort());
            }
            textView.setText(title + getProxyTypeSuffix(proxyInfo));
            checkImageView.setOnClickListener(v -> presentFragment(new ProxySettingsActivity(currentInfo)));
        }

        private String getProxyTypeSuffix(SharedConfig.ProxyInfo proxyInfo) {
            if (proxyInfo == null || proxyInfo.settings == null) {
                return "";
            }
            if (proxyInfo.unrecognized) {
                return "";
            }
            if (proxyInfo.isAether()) {
                String detail = !TextUtils.isEmpty(proxyInfo.aetherProtocol) ? proxyInfo.aetherProtocol.toUpperCase(Locale.US) : "";
                return TextUtils.isEmpty(detail) ? " (Aether)" : " (Aether " + detail + ")";
            }
            ProxySettings.Type type = proxyInfo.settings.getType();
            if (type == ProxySettings.Type.MTPROTO) {
                return " (MTProto)";
            }
            if (type == ProxySettings.Type.SOCKS5) {
                return " (SOCKS5)";
            }
            if (type == ProxySettings.Type.WEB) {
                return " (WEB)";
            }
            if (proxyInfo.isXrayVless()) {
                String protocol = "VLESS";
                if (!TextUtils.isEmpty(proxyInfo.vlessAdvancedJson)) {
                    try {
                        String jsonProtocol = new org.json.JSONObject(proxyInfo.vlessAdvancedJson).optString("protocol", "");
                        if ("vmess".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "VMess";
                        } else if ("trojan".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "Trojan";
                        } else if ("shadowsocks".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "SS";
                        } else if ("socks".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "SOCKS";
                        } else if ("http".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "HTTP";
                        } else if ("wireguard".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "WireGuard";
                        } else if ("hysteria2".equalsIgnoreCase(jsonProtocol) || "hy2".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "Hysteria2";
                        } else if ("hysteria".equalsIgnoreCase(jsonProtocol)) {
                            protocol = "Hysteria";
                        } else if (!TextUtils.isEmpty(jsonProtocol) && !"vless".equalsIgnoreCase(jsonProtocol)) {
                            protocol = jsonProtocol.toUpperCase(Locale.US);
                        }
                    } catch (Throwable ignored) {
                    }
                }
                return " (" + protocol + ")";
            }
            return "";
        }

        public void updateStatus() {
            if (currentInfo != null && currentInfo.unrecognized) {
                valueTextView.setText(getString(R.string.ProxyUnrecognizable));
                valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
                return;
            }
            int colorKey;
            if (SharedConfig.currentProxy == currentInfo && useProxySettings) {
                if (currentConnectionState == ConnectionsManager.ConnectionStateConnected || currentConnectionState == ConnectionsManager.ConnectionStateUpdating) {
                    colorKey = Theme.key_windowBackgroundWhiteBlueText6;
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(getString(R.string.Connected) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(getString(R.string.Connected));
                    }
                    if (!currentInfo.checking && !currentInfo.available) {
                        currentInfo.availableCheckTime = 0;
                    }
                } else {
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                    valueTextView.setText(getString(R.string.Connecting));
                }
            } else {
                if (currentInfo.checking) {
                    valueTextView.setText(getString(R.string.Checking));
                    colorKey = Theme.key_windowBackgroundWhiteGrayText2;
                } else if (currentInfo.available) {
                    if (currentInfo.ping != 0) {
                        valueTextView.setText(getString(R.string.Available) + ", " + LocaleController.formatString("Ping", R.string.Ping, currentInfo.ping));
                    } else {
                        valueTextView.setText(getString(R.string.Available));
                    }
                    colorKey = Theme.key_windowBackgroundWhiteGreenText;
                } else {
                    valueTextView.setText(getString(R.string.Unavailable));
                    colorKey = Theme.key_text_RedRegular;
                }
            }
            color = Theme.getColor(colorKey);
            valueTextView.setTag(colorKey);
            valueTextView.setTextColor(color);
            if (checkDrawable != null) {
                checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            }
        }

        public void setSelectionEnabled(boolean enabled, boolean animated) {
            if (isSelectionEnabled == enabled && animated) {
                return;
            }
            isSelectionEnabled = enabled;

            float fromX = 0, toX = LocaleController.isRTL ? -AndroidUtilities.dp(32) : AndroidUtilities.dp(32);
            if (!animated) {
                float x = enabled ? toX : fromX;
                textView.setTranslationX(x);
                valueTextView.setTranslationX(x);
                checkImageView.setTranslationX(x);
                checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);
                checkImageView.setVisibility(enabled ? GONE : VISIBLE);
                checkImageView.setAlpha(1f);
                checkImageView.setScaleX(1f);
                checkImageView.setScaleY(1f);
                checkBox.setVisibility(enabled ? VISIBLE : GONE);
                checkBox.setAlpha(1f);
                checkBox.setScaleX(1f);
                checkBox.setScaleY(1f);
            } else {
                ValueAnimator animator = ValueAnimator.ofFloat(enabled ? 0 : 1, enabled ? 1 : 0).setDuration(200);
                animator.setInterpolator(CubicBezierInterpolator.DEFAULT);
                animator.addUpdateListener(animation -> {
                    float val = (float) animation.getAnimatedValue();
                    float x = AndroidUtilities.lerp(fromX, toX, val);
                    textView.setTranslationX(x);
                    valueTextView.setTranslationX(x);
                    checkImageView.setTranslationX(x);
                    checkBox.setTranslationX((LocaleController.isRTL ? AndroidUtilities.dp(32) : -AndroidUtilities.dp(32)) + x);

                    float scale = 0.5f + val * 0.5f;
                    checkBox.setScaleX(scale);
                    checkBox.setScaleY(scale);
                    checkBox.setAlpha(val);

                    scale = 0.5f + (1f - val) * 0.5f;
                    checkImageView.setScaleX(scale);
                    checkImageView.setScaleY(scale);
                    checkImageView.setAlpha(1f - val);
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        if (enabled) {
                            checkBox.setAlpha(0f);
                            checkBox.setVisibility(VISIBLE);
                        } else {
                            checkImageView.setAlpha(0f);
                            checkImageView.setVisibility(VISIBLE);
                        }
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (enabled) {
                            checkImageView.setVisibility(GONE);
                        } else {
                            checkBox.setVisibility(GONE);
                        }
                    }
                });
                animator.start();
            }
        }

        public void setItemSelected(boolean selected, boolean animated) {
            if (selected == isSelected && animated) {
                return;
            }
            isSelected = selected;
            checkBox.setChecked(selected, animated);
        }

        public void setChecked(boolean checked) {
            if (checked) {
                if (checkDrawable == null) {
                    checkDrawable = getResources().getDrawable(R.drawable.proxy_check).mutate();
                }
                if (checkDrawable != null) {
                    checkDrawable.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
                }
                if (LocaleController.isRTL) {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, checkDrawable, null);
                } else {
                    valueTextView.setCompoundDrawablesWithIntrinsicBounds(checkDrawable, null, null, null);
                }
            } else {
                valueTextView.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }
        }

        public void setValue(CharSequence value) {
            valueTextView.setText(value);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            updateStatus();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    private class SubscriptionGroupCell extends FrameLayout {
        private final TextView titleView;
        private final TextView announceView;
        private final TextView quotaView;
        private final TextView metaView;
        private final TextView supportButton;
        private final TextView websiteButton;
        private final LinearLayout linksRow;
        private final ImageView menuView;
        private final ImageView pingView;
        private final ImageView refreshView;
        private final ImageView collapseView;
        private final LineProgressView progressView;
        private SubscriptionGroup boundGroup;

        public SubscriptionGroupCell(Context context) {
            super(context);
            setWillNotDraw(false);

            titleView = new TextView(context);
            titleView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            titleView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            titleView.setTypeface(AndroidUtilities.bold());
            titleView.setMaxLines(1);
            titleView.setEllipsize(TextUtils.TruncateAt.END);
            addView(titleView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 12, 120, 0));
            titleView.setClickable(true);
            titleView.setFocusable(true);

            menuView = createIcon(R.drawable.ic_ab_other, R.string.AccDescrMoreOptions);
            pingView = createIcon(R.drawable.msg_proxy_ping, R.string.ProxySubscriptionTestPing);
            refreshView = createIcon(R.drawable.msg_proxy_refresh, R.string.Refresh);
            collapseView = createIcon(R.drawable.arrow_more, R.string.AccDescrExpandPanel);

            addView(menuView, LayoutHelper.createFrame(36, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 8 : 0, 8, LocaleController.isRTL ? 0 : 8, 0));
            addView(pingView, LayoutHelper.createFrame(36, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 44 : 0, 8, LocaleController.isRTL ? 0 : 44, 0));
            addView(refreshView, LayoutHelper.createFrame(36, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 80 : 0, 8, LocaleController.isRTL ? 0 : 80, 0));
            addView(collapseView, LayoutHelper.createFrame(36, 36, Gravity.TOP | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), LocaleController.isRTL ? 116 : 0, 8, LocaleController.isRTL ? 0 : 116, 0));

            progressView = new LineProgressView(context);
            progressView.setProgressColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            progressView.setBackColor(Theme.getColor(Theme.key_player_progressBackground));
            addView(progressView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 3, Gravity.TOP | Gravity.LEFT, 21, 44, 21, 0));

            quotaView = new TextView(context);
            quotaView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            quotaView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            addView(quotaView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 52, 21, 0));

            announceView = new TextView(context);
            announceView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            announceView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
            announceView.setMaxLines(3);
            announceView.setEllipsize(TextUtils.TruncateAt.END);
            addView(announceView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 72, 21, 0));

            linksRow = new LinearLayout(context);
            linksRow.setOrientation(LinearLayout.HORIZONTAL);
            int linkSelector = Theme.getColor(Theme.key_listSelector);
            supportButton = new TextView(context);
            supportButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            supportButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            supportButton.setText(getString(R.string.ProxySubscriptionSupport));
            supportButton.setMinHeight(AndroidUtilities.dp(36));
            supportButton.setGravity(Gravity.CENTER_VERTICAL);
            supportButton.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(8), AndroidUtilities.dp(12), AndroidUtilities.dp(8));
            supportButton.setBackground(Theme.createSelectorDrawable(linkSelector));
            supportButton.setClickable(true);
            supportButton.setFocusable(true);
            websiteButton = new TextView(context);
            websiteButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            websiteButton.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
            websiteButton.setText(getString(R.string.ProxySubscriptionWebsite));
            websiteButton.setMinHeight(AndroidUtilities.dp(36));
            websiteButton.setGravity(Gravity.CENTER_VERTICAL);
            websiteButton.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(8), AndroidUtilities.dp(21), AndroidUtilities.dp(8));
            websiteButton.setBackground(Theme.createSelectorDrawable(linkSelector));
            websiteButton.setClickable(true);
            websiteButton.setFocusable(true);
            linksRow.addView(supportButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            linksRow.addView(websiteButton, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            addView(linksRow, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 96, 0, 0));

            metaView = new TextView(context);
            metaView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            metaView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            metaView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
            addView(metaView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 21, 0, 21, 10));
        }

        private ImageView createIcon(int resId, int contentDescriptionRes) {
            ImageView view = new ImageView(getContext());
            view.setScaleType(ImageView.ScaleType.CENTER);
            view.setImageResource(resId);
            view.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
            view.setContentDescription(getString(contentDescriptionRes));
            view.setClickable(true);
            return view;
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(140), MeasureSpec.EXACTLY));
        }

        public void bind(SubscriptionGroup group, boolean collapsed, boolean showCollapse) {
            boundGroup = group;
            titleView.setText(group != null ? group.name : "");
            collapseView.setVisibility(showCollapse ? View.VISIBLE : View.GONE);
            collapseView.setRotation(collapsed ? 0f : 180f);

            XraySubscriptionStore.Entry entry = group != null ? findEntryForGroup(group) : null;

            if (entry != null && entry.total > 0) {
                long used = entry.upload + entry.download;
                progressView.setProgress(Math.min(1f, used / (float) entry.total), false);
                progressView.setVisibility(VISIBLE);
                quotaView.setText(LocaleController.formatString(R.string.ProxySubscriptionQuota, AndroidUtilities.formatFileSize(used), AndroidUtilities.formatFileSize(entry.total)));
                quotaView.setVisibility(VISIBLE);
            } else {
                progressView.setVisibility(GONE);
                quotaView.setText(getString(R.string.ProxySubscriptionQuotaUnlimited));
                quotaView.setVisibility(VISIBLE);
            }

            if (entry != null && !TextUtils.isEmpty(entry.announce)) {
                announceView.setText(entry.announce);
                announceView.setVisibility(VISIBLE);
            } else {
                announceView.setVisibility(GONE);
            }

            if (entry != null && !TextUtils.isEmpty(entry.supportUrl)) {
                supportButton.setVisibility(VISIBLE);
                supportButton.setOnClickListener(v -> Browser.openUrl(getContext(), entry.supportUrl));
            } else {
                supportButton.setVisibility(GONE);
            }
            if (entry != null && !TextUtils.isEmpty(entry.webPageUrl)) {
                websiteButton.setVisibility(VISIBLE);
                websiteButton.setOnClickListener(v -> Browser.openUrl(getContext(), entry.webPageUrl));
            } else {
                websiteButton.setVisibility(GONE);
            }

            updateMetaRow(group, entry);
            linksRow.bringToFront();

            menuView.setOnClickListener(v -> showSubscriptionGroupMenu(group, v));
            pingView.setOnClickListener(v -> {
                if (group == null) {
                    return;
                }
                for (SharedConfig.ProxyInfo info : group.proxies) {
                    if (info.unrecognized) {
                        continue;
                    }
                    info.checking = false;
                    info.availableCheckTime = 0;
                }
                checkProxyList(true);
                updateVisibleProxyStatuses();
            });
        }

        public void updateMetaRow(SubscriptionGroup group, XraySubscriptionStore.Entry entry) {
            if (entry == null && group != null) {
                entry = findEntryForGroup(group);
            }
            int count = group != null ? group.proxies.size() : 0;
            if (entry != null && entry.configCount > 0) {
                count = entry.configCount;
            }
            String countText = LocaleController.formatString(R.string.ProxySubscriptionConfigs, count);
            String updated = entry != null && entry.lastUpdateTime > 0
                    ? LocaleController.formatString(R.string.ProxySubscriptionLastUpdated, LocaleController.formatShortDate(entry.lastUpdateTime / 1000))
                    : "";
            String intervalPart = "";
            if (entry != null && entry.autoUpdate && entry.updateIntervalMinutes > 0) {
                intervalPart = formatRefreshInterval(entry.updateIntervalMinutes);
            }
            SpannableStringBuilder meta = new SpannableStringBuilder();
            meta.append(" ");
            meta.setSpan(new ColoredImageSpan(R.drawable.msg_proxy_server, DynamicDrawableSpan.ALIGN_CENTER), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            meta.append(" ");
            meta.append(countText);
            if (!TextUtils.isEmpty(updated)) {
                meta.append(" · ");
                meta.append(updated);
            }
            if (!TextUtils.isEmpty(intervalPart)) {
                meta.append(" · ");
                meta.append(intervalPart);
            }
            metaView.setText(meta);
        }

        public void setOnRefreshClickListener(OnClickListener listener) {
            refreshView.setOnClickListener(listener);
        }

        public void setOnCollapseClickListener(OnClickListener listener) {
            collapseView.setOnClickListener(listener);
            titleView.setOnClickListener(listener);
        }

        private XraySubscriptionStore.Entry findEntryForGroup(SubscriptionGroup group) {
            if (group == null) {
                return null;
            }
            if (!TextUtils.isEmpty(group.subscriptionUrl)) {
                XraySubscriptionStore.Entry entry = XraySubscriptionStore.getByUrl(group.subscriptionUrl);
                if (entry != null) {
                    return entry;
                }
            }
            return XraySubscriptionStore.getByDisplayTitle(group.name);
        }

        private String formatRefreshInterval(int minutes) {
            if (minutes % 60 == 0 && minutes >= 60) {
                return LocaleController.formatString(R.string.ProxySubscriptionRefreshInterval, (minutes / 60) + "h");
            }
            return LocaleController.formatString(R.string.ProxySubscriptionRefreshInterval, minutes + "m");
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();

        SharedConfig.loadProxyList();
        currentConnectionState = ConnectionsManager.getInstance(currentAccount).getConnectionState();

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.didUpdateConnectionState);

        final SharedPreferences preferences = MessagesController.getGlobalMainSettings();
        useProxySettings = preferences.getBoolean("proxy_enabled", false) && !SharedConfig.proxyList.isEmpty();
        useProxyForCalls = preferences.getBoolean("proxy_enabled_calls", false);

        updateRows(true);
        scheduleSubscriptionMetaRefresh();

        return true;
    }

    @Override
    public void onFragmentDestroy() {
        if (subscriptionMetaRefreshRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(subscriptionMetaRefreshRunnable);
        }
        super.onFragmentDestroy();
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyChangedByRotation);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.proxyCheckDone);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.didUpdateConnectionState);
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonDrawable(new BackDrawable(false));
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.ProxySettings));
        if (parentLayout != null && parentLayout.isLayersLayout()) {
            actionBar.setOccupyStatusBar(false);
        }
        actionBar.setAllowOverlayTitle(false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem pasteItem = menu.addItem(menu_paste_clipboard, R.drawable.msg_paste);
        pasteItem.setContentDescription(getString(R.string.PasteFromClipboard));
        pasteItem.setOnClickListener(v -> ProxyUtil.importFromClipboard(getParentActivity()));
        ActionBarMenuItem addProxyItem = menu.addItem(menu_add_proxy, R.drawable.msg_add);
        addProxyItem.setContentDescription(getString(R.string.AddProxy));
        addProxyItem.setOnClickListener(v -> showAddProxyPlusDialog());
        ActionBarMenuItem otherItem = menu.addItem(0, R.drawable.ic_ab_other);
        otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        otherItem.addSubItem(menu_add_input_telegram, getString(R.string.AddProxyTelegram)).setOnClickListener((v) -> presentFragment(new ProxySettingsActivity()));
        otherItem.addSubItem(menu_import_clipboard, getString(R.string.ImportProxyFromClipboard)).setOnClickListener((v) -> ProxyUtil.importFromClipboard(getParentActivity()));
        otherItem.addSubItem(menu_import_url, getString(R.string.ImportProxyFromUrl)).setOnClickListener((v) -> showImportFromUrlDialog());
        otherItem.addSubItem(menu_retest_ping, getString(R.string.RetestPing)).setOnClickListener((v) -> {
            checkProxyList(true);
            updateVisibleProxyStatuses();
        });
        otherItem.addSubItem(menu_refresh_subscriptions, getString(R.string.RefreshProxySubscriptions)).setOnClickListener((v) -> ProxyUtil.refreshSubscriptions(getParentActivity()));
        otherItem.addSubItem(menu_delete_all, getString(R.string.DeleteAllServer)).setOnClickListener((v) -> {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(getString(R.string.DeleteAllProxiesConfirm));
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.setTitle(getString(R.string.DeleteAllServer));
            builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                for (SharedConfig.ProxyInfo info : new ArrayList<>(proxyList)) {
                    SharedConfig.deleteProxy(info);
                }
                useProxyForCalls = false;
                useProxySettings = false;
                updateRows(true);
            });
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });
        otherItem.addSubItem(menu_delete_unavailable, getString(R.string.DeleteUnavailableServer)).setOnClickListener((v) -> {
            if (getParentActivity() == null) {
                return;
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
            builder.setMessage(getString(R.string.DeleteUnavailableServer));
            builder.setNegativeButton(getString(R.string.Cancel), null);
            builder.setTitle(getString(R.string.DeleteUnavailableServer));
            builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                for (SharedConfig.ProxyInfo info : new ArrayList<>(SharedConfig.getProxyList())) {
                    if (info.checking) {
                        continue;
                    }
                    if (!info.available) {
                        SharedConfig.deleteProxy(info);
                    }
                }
                if (SharedConfig.currentProxy == null) {
                    useProxyForCalls = false;
                    useProxySettings = false;
                }
                NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                updateRows(true);
                if (listAdapter != null) {
                    if (SharedConfig.currentProxy == null) {
                        listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                        listAdapter.notifyItemChanged(callsRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                    }
                    listAdapter.clearSelected();
                }
            });
            AlertDialog dialog = builder.create();
            showDialog(dialog);
            TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        fragmentView.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        FrameLayout frameLayout = (FrameLayout) fragmentView;

        listView = new RecyclerListView(context);
        listView.setSections();
        actionBar.setAdaptiveBackground(listView);
        ((DefaultItemAnimator) listView.getItemAnimator()).setDelayAnimations(false);
        ((DefaultItemAnimator) listView.getItemAnimator()).setTranslationInterpolator(CubicBezierInterpolator.DEFAULT);
        listView.setVerticalScrollBarEnabled(false);
        listView.setLayoutManager(layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (position == useProxyRow) {
                if (SharedConfig.currentProxy == null) {
                    if (!proxyList.isEmpty()) {
                        SharedConfig.currentProxy = proxyList.get(0);

                        if (!useProxySettings) {
                            SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                            SharedConfig.currentProxy.settings.toSharedPreferences(editor);
                            editor.commit();
                        }
                    } else {
                        presentFragment(new ProxySettingsActivity());
                        return;
                    }
                }
                useProxySettings = !useProxySettings;
                updateRows(true);

                SharedPreferences preferences = MessagesController.getGlobalMainSettings();

                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(useProxySettings);
                if (!useProxySettings) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(callsRow);
                    if (holder != null) {
                        textCheckCell = (TextCheckCell) holder.itemView;
                        textCheckCell.setChecked(false);
                    }
                    useProxyForCalls = false;
                }

                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putBoolean("proxy_enabled", useProxySettings);
                editor.commit();

                ConnectionsManager.setProxySettings(useProxySettings, SharedConfig.currentProxy.settings);
                NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);

                updateVisibleProxyStatuses();
            } else if (position == rotationRow) {
                SharedConfig.proxyRotationEnabled = !SharedConfig.proxyRotationEnabled;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(SharedConfig.proxyRotationEnabled);
                SharedConfig.saveConfig();

                updateRows(true);
            } else if (position == callsRow) {
                useProxyForCalls = !useProxyForCalls;
                TextCheckCell textCheckCell = (TextCheckCell) view;
                textCheckCell.setChecked(useProxyForCalls);
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                editor.putBoolean("proxy_enabled_calls", useProxyForCalls);
                editor.commit();
            } else if (isProxyPosition(position)) {
                if (!selectedItems.isEmpty()) {
                    listAdapter.toggleSelected(position);
                    return;
                }
                SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
                if (info == null || info.unrecognized) {
                    return;
                }
                useProxySettings = true;
                SharedPreferences.Editor editor = MessagesController.getGlobalMainSettings().edit();
                info.settings.toSharedPreferences(editor);
                editor.putBoolean("proxy_enabled", useProxySettings);
                if (!TextUtils.isEmpty(info.settings.getSecret())) {
                    useProxyForCalls = false;
                    editor.putBoolean("proxy_enabled_calls", false);
                }
                editor.commit();
                SharedConfig.currentProxy = info;
                updateVisibleProxySelection(info);
                updateRows(false);
                RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(useProxyRow);
                if (holder != null) {
                    TextCheckCell textCheckCell = (TextCheckCell) holder.itemView;
                    textCheckCell.setChecked(true);
                }
                ConnectionsManager.setProxySettings(useProxySettings, SharedConfig.currentProxy.settings);
            } else if (position == proxyAddRow) {
                presentFragment(new ProxySettingsActivity());
            } else if (position == deleteAllRow) {
                AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                builder.setMessage(getString(R.string.DeleteAllProxiesConfirm));
                builder.setNegativeButton(getString(R.string.Cancel), null);
                builder.setTitle(getString(R.string.DeleteProxyTitle));
                builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        SharedConfig.deleteProxy(info);
                    }
                    useProxyForCalls = false;
                    useProxySettings = false;
                    NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                    updateRows(true);
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                        listAdapter.notifyItemChanged(callsRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                        listAdapter.clearSelected();
                    }
                });
                AlertDialog dialog = builder.create();
                showDialog(dialog);
                TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                if (button != null) {
                    button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                }
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (isProxyPosition(position)) {
                listAdapter.toggleSelected(position);
                return true;
            }
            return false;
        });
        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getAdapterPosition();
                if (position == RecyclerView.NO_POSITION || !isSubscriptionGroupPosition(position) || !selectedItems.isEmpty()) {
                    return 0;
                }
                return makeMovementFlags(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                SubscriptionRow row = getSubscriptionRow(position);
                if (row == null || !row.isGroup || row.group == null) {
                    if (listAdapter != null) {
                        listAdapter.notifyItemChanged(position);
                    }
                    return;
                }
                confirmDeleteSubscriptionGroup(row.group, position);
            }
        });
        itemTouchHelper.attachToRecyclerView(listView);

        ActionBarMenu actionMode = actionBar.createActionMode();
        selectedCountTextView = new NumberTextView(actionMode.getContext());
        selectedCountTextView.setTextSize(18);
        selectedCountTextView.setTypeface(AndroidUtilities.bold());
        selectedCountTextView.setTextColor(Theme.getColor(Theme.key_actionBarActionModeDefaultIcon));
        actionMode.addView(selectedCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 72, 0, 0, 0));
        selectedCountTextView.setOnTouchListener((v, event) -> true);

        shareMenuItem = actionMode.addItemWithWidth(MENU_SHARE, R.drawable.msg_share, AndroidUtilities.dp(54));
        shareMenuItem.setContentDescription(getString(R.string.StickersShare));
        deleteMenuItem = actionMode.addItemWithWidth(MENU_DELETE, R.drawable.msg_delete, AndroidUtilities.dp(54));
        deleteMenuItem.setContentDescription(getString(R.string.Delete));

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                switch (id) {
                    case -1:
                        if (selectedItems.isEmpty()) {
                            finishFragment();
                        } else {
                            listAdapter.clearSelected();
                        }
                        break;
                    case MENU_DELETE:
                        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
                        builder.setMessage(getString(selectedItems.size() > 1 ? R.string.DeleteProxyMultiConfirm : R.string.DeleteProxyConfirm));
                        builder.setNegativeButton(getString(R.string.Cancel), null);
                        builder.setTitle(getString(R.string.DeleteProxyTitle));
                        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> {
                            for (SharedConfig.ProxyInfo info : selectedItems) {
                                SharedConfig.deleteProxy(info);
                            }
                            if (SharedConfig.currentProxy == null) {
                                useProxySettings = false;
                            }
                            NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                            NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
                            updateRows(true);
                            if (listAdapter != null) {
                                if (SharedConfig.currentProxy == null) {
                                    listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                                }
                                listAdapter.clearSelected();
                            }
                        });
                        AlertDialog dialog = builder.create();
                        showDialog(dialog);
                        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                        if (button != null) {
                            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
                        }
                        break;
                    case MENU_SHARE:
                        StringBuilder links = new StringBuilder();
                        for (SharedConfig.ProxyInfo info : selectedItems) {
                            if (links.length() > 0) {
                                links.append("\n\n");
                            }
                            links.append(info.settings.getLink());
                        }

                        Intent shareIntent = new Intent(Intent.ACTION_SEND);
                        shareIntent.setType("text/plain");
                        shareIntent.putExtra(Intent.EXTRA_TEXT, links.toString());
                        Intent chooserIntent = Intent.createChooser(shareIntent, getString(selectedItems.size() > 1 ? R.string.ShareLinks : R.string.ShareLink));
                        chooserIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(chooserIntent);

                        if (listAdapter != null) {
                            listAdapter.clearSelected();
                        }
                        break;
                }
            }
        });

        return fragmentView;
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (!selectedItems.isEmpty()) {
            if (invoked) listAdapter.clearSelected();
            return false;
        }
        return super.onBackPressed(invoked);
    }

    private int getPositionForGroup(SubscriptionGroup group) {
        if (subscriptionStartRow == -1 || group == null) {
            return -1;
        }
        for (int i = 0; i < subscriptionRows.size(); i++) {
            SubscriptionRow row = subscriptionRows.get(i);
            if (row.isGroup && row.group == group) {
                return subscriptionStartRow + i;
            }
        }
        return -1;
    }

    private void showAddProxyPlusDialog() {
        if (getParentActivity() == null) {
            return;
        }
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);

        TextSettingsCell pasteCell = new TextSettingsCell(getParentActivity());
        pasteCell.setBackgroundColor(Theme.getColor(Theme.key_dialogBackgroundGray));
        pasteCell.setBackground(Theme.getSelectorDrawable(Theme.getColor(Theme.key_dialogButtonSelector), false));
        pasteCell.setText(getString(R.string.PasteFromClipboard), false);
        pasteCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText4));
        pasteCell.setOnClickListener(v -> ProxyUtil.importFromClipboard(getParentActivity()));
        container.addView(pasteCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 12, 16, 12));

        View pasteDivider = new View(getParentActivity());
        pasteDivider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        container.addView(pasteDivider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1, 16, 0, 16, 4));

        FrameLayout sectionHeader = new FrameLayout(getParentActivity());
        TextView sectionTitle = new TextView(getParentActivity());
        sectionTitle.setText(getString(R.string.AddXraySubscription));
        sectionTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        sectionTitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        sectionHeader.addView(sectionTitle, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT), 24, 0, 48, 0));
        ImageView expandView = new ImageView(getParentActivity());
        expandView.setImageResource(R.drawable.arrow_more);
        expandView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        sectionHeader.addView(expandView, LayoutHelper.createFrame(36, 36, Gravity.CENTER_VERTICAL | (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT), 8, 0, 8, 0));
        container.addView(sectionHeader, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        LinearLayout subscriptionForm = new LinearLayout(getParentActivity());
        subscriptionForm.setOrientation(LinearLayout.VERTICAL);
        subscriptionForm.setVisibility(View.GONE);

        OutlineTextContainerView remarkOutline = new OutlineTextContainerView(getParentActivity());
        remarkOutline.setText(getString(R.string.ProxySubscriptionRemark));
        remarkOutline.animateSelection(0f, false);
        EditTextBoldCursor remarkField = createOutlinedField(remarkOutline, "");
        subscriptionForm.addView(remarkOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 12));

        OutlineTextContainerView urlOutline = new OutlineTextContainerView(getParentActivity());
        urlOutline.setText(getString(R.string.ProxySubscriptionUrl));
        urlOutline.animateSelection(0f, false);
        EditTextBoldCursor urlField = createOutlinedField(urlOutline, "");
        subscriptionForm.addView(urlOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 0, 16, 0));

        TextCheckCell[] autoUpdateCellHolder = new TextCheckCell[1];
        int[] selectedMinutes = new int[]{1440};
        subscriptionForm.addView(buildScheduleSection(true, 1440, autoUpdateCellHolder, selectedMinutes), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        container.addView(subscriptionForm, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        sectionHeader.setOnClickListener(v -> {
            boolean show = subscriptionForm.getVisibility() != View.VISIBLE;
            subscriptionForm.setVisibility(show ? View.VISIBLE : View.GONE);
            expandView.setRotation(show ? 180f : 0f);
        });

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.AddProxy));
        builder.setView(container);
        builder.setPositiveButton(getString(R.string.Import), (dialog, which) -> {
            String url = urlField.getText().toString().trim();
            if (TextUtils.isEmpty(url)) {
                return;
            }
            boolean autoUpdate = autoUpdateCellHolder[0] != null && autoUpdateCellHolder[0].isChecked();
            ProxyUtil.importSubscriptionWithOptions(
                    getParentActivity(),
                    url,
                    remarkField.getText().toString().trim(),
                    autoUpdate,
                    selectedMinutes[0],
                    true
            );
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private EditTextBoldCursor createDialogField(String hint) {
        EditTextBoldCursor field = new EditTextBoldCursor(getParentActivity());
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        field.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setHintText(hint);
        field.setBackgroundDrawable(null);
        field.setPadding(AndroidUtilities.dp(4), AndroidUtilities.dp(10), AndroidUtilities.dp(4), AndroidUtilities.dp(10));
        return field;
    }

    private FrameLayout wrapDialogField(EditTextBoldCursor field) {
        FrameLayout wrap = new FrameLayout(getParentActivity());
        wrap.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 8, 0, 8, 0));
        return wrap;
    }

    private void showImportFromUrlDialog() {
        if (getParentActivity() == null) {
            return;
        }
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);

        EditTextBoldCursor editText = new EditTextBoldCursor(getParentActivity());
        editText.setSingleLine(true);
        editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        editText.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setHintColor(Theme.getColor(Theme.key_groupcreate_hintText));
        editText.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        editText.setLineColors(Theme.getColor(Theme.key_windowBackgroundWhiteInputField), Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated), Theme.getColor(Theme.key_text_RedRegular));
        editText.setHintText(LocaleController.getString(R.string.ProxySubscriptionUrl));
        editText.setBackgroundDrawable(null);
        container.addView(editText, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 24, 0, 24, 10));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.ImportProxyFromUrl));
        builder.setView(container);
        builder.setPositiveButton(LocaleController.getString(R.string.Import), (dialog, which) -> {
            String url = editText.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                ProxyUtil.importFromUrl(getParentActivity(), url, true);
            }
        });
        builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
        showDialog(builder.create());
    }

    private void updateRows(boolean notify) {
        rowCount = 0;
        useProxyRow = rowCount++;
        if (useProxySettings && SharedConfig.currentProxy != null && SharedConfig.proxyList.size() > 1 && IS_PROXY_ROTATION_AVAILABLE) {
            rotationRow = rowCount++;
            if (SharedConfig.proxyRotationEnabled) {
                rotationTimeoutRow = rowCount++;
                rotationTimeoutInfoRow = rowCount++;
            } else {
                rotationTimeoutRow = -1;
                rotationTimeoutInfoRow = -1;
            }
        } else {
            rotationRow = -1;
            rotationTimeoutRow = -1;
            rotationTimeoutInfoRow = -1;
        }
        if (rotationTimeoutInfoRow == -1) {
            useProxyShadowRow = rowCount++;
        } else {
            useProxyShadowRow = -1;
        }
        connectionsHeaderRow = rowCount++;

        if (notify) {
            proxyList.clear();
            proxyList.addAll(SharedConfig.proxyList);

            boolean checking = false;
            if (!wasCheckedAllList) {
                for (SharedConfig.ProxyInfo info : proxyList) {
                    if (info.checking || info.availableCheckTime == 0) {
                        checking = true;
                        break;
                    }
                }
                if (!checking) {
                    wasCheckedAllList = true;
                }
            }

        }

        if (proxyList.isEmpty()) {
            proxyList.addAll(SharedConfig.proxyList);
        }
        subscriptionProxyList.clear();
        manualProxyList.clear();
        for (SharedConfig.ProxyInfo info : proxyList) {
            if (info.isSubscription) {
                subscriptionProxyList.add(info);
            } else {
                manualProxyList.add(info);
            }
        }
        if (notify) {
            sortManualProxyList(checkingForManualSort());
        }
        rebuildSubscriptionRows();

        boolean hasSubscriptionSection = !subscriptionProxyList.isEmpty() || !XraySubscriptionStore.getAll().isEmpty();
        if (hasSubscriptionSection) {
            subscriptionHeaderRow = rowCount++;
            subscriptionStartRow = rowCount;
            rowCount += subscriptionRows.size();
            subscriptionEndRow = rowCount;
            proxyStartRow = subscriptionStartRow;
            proxyEndRow = subscriptionEndRow;
        } else {
            subscriptionHeaderRow = -1;
            subscriptionStartRow = -1;
            subscriptionEndRow = -1;
        }
        if (!manualProxyList.isEmpty()) {
            manualHeaderRow = rowCount++;
            manualStartRow = rowCount;
            rowCount += manualProxyList.size();
            manualEndRow = rowCount;
            if (subscriptionStartRow == -1) {
                proxyStartRow = manualStartRow;
                proxyEndRow = manualEndRow;
            } else {
                proxyStartRow = subscriptionStartRow;
                proxyEndRow = manualEndRow;
            }
        } else {
            manualHeaderRow = -1;
            manualStartRow = -1;
            manualEndRow = -1;
            if (subscriptionStartRow == -1) {
                proxyStartRow = -1;
                proxyEndRow = -1;
            }
        }
        proxyAddRow = rowCount++;
        proxyShadowRow = rowCount++;
        subscriptionUserAgentRow = -1;
        if (SharedConfig.currentProxy == null || TextUtils.isEmpty(SharedConfig.currentProxy.settings.getSecret())) {
            callsRow = rowCount++;
            callsDetailRow = rowCount++;
        } else {
            callsRow = -1;
            callsDetailRow = -1;
        }
        if (proxyList.size() >= 10) {
            deleteAllRow = rowCount++;
        } else {
            deleteAllRow = -1;
        }
        checkProxyList();
        if (notify && listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private boolean checkingForManualSort() {
        if (wasCheckedAllList) {
            return false;
        }
        for (SharedConfig.ProxyInfo info : manualProxyList) {
            if (info.checking || info.availableCheckTime == 0) {
                return true;
            }
        }
        return false;
    }

    private void sortManualProxyList(boolean isChecking) {
        Collections.sort(manualProxyList, (o1, o2) -> compareManualProxyOrder(o1, o2, isChecking));
    }

    private int compareManualProxyOrder(SharedConfig.ProxyInfo o1, SharedConfig.ProxyInfo o2, boolean isChecking) {
        long bias1 = SharedConfig.currentProxy == o1 ? -200000 : 0;
        if (!o1.available) {
            bias1 += 100000;
        }
        long bias2 = SharedConfig.currentProxy == o2 ? -200000 : 0;
        if (!o2.available) {
            bias2 += 100000;
        }
        long score1 = isChecking && o1 != SharedConfig.currentProxy ? SharedConfig.proxyList.indexOf(o1) * 10000L : o1.ping + bias1;
        long score2 = isChecking && o2 != SharedConfig.currentProxy ? SharedConfig.proxyList.indexOf(o2) * 10000L : o2.ping + bias2;
        return Long.compare(score1, score2);
    }

    private XraySubscriptionStore.Entry getEntryForGroup(SubscriptionGroup group) {
        if (group == null) {
            return null;
        }
        if (!TextUtils.isEmpty(group.subscriptionUrl)) {
            XraySubscriptionStore.Entry entry = XraySubscriptionStore.getByUrl(group.subscriptionUrl);
            if (entry != null) {
                return entry;
            }
        }
        return XraySubscriptionStore.getByDisplayTitle(group.name);
    }

    private String getProxySortName(SharedConfig.ProxyInfo info) {
        if (info == null) {
            return "";
        }
        if (!TextUtils.isEmpty(info.vlessRemark)) {
            return info.vlessRemark;
        }
        if (!TextUtils.isEmpty(info.proxyName)) {
            return info.proxyName;
        }
        if (info.settings != null) {
            return info.settings.getAddress();
        }
        return "";
    }

    private void sortSubscriptionGroupProxies(SubscriptionGroup group) {
        if (group == null || group.proxies.isEmpty()) {
            return;
        }
        XraySubscriptionStore.Entry entry = getEntryForGroup(group);
        String mode = entry != null && !TextUtils.isEmpty(entry.sortMode) ? entry.sortMode : SORT_ADDED;
        if (SORT_PING.equals(mode)) {
            Collections.sort(group.proxies, (a, b) -> compareManualProxyOrder(a, b, checkingForManualSort()));
        } else if (SORT_NAME.equals(mode)) {
            Collections.sort(group.proxies, (a, b) -> getProxySortName(a).compareToIgnoreCase(getProxySortName(b)));
        }
    }

    private void rebuildSubscriptionRows() {
        subscriptionGroups.clear();
        subscriptionRows.clear();
        LinkedHashMap<String, SubscriptionGroup> map = new LinkedHashMap<>();
        for (XraySubscriptionStore.Entry entry : XraySubscriptionStore.getAll()) {
            String name = entry.getDisplayTitle();
            SubscriptionGroup group = map.get(name);
            if (group == null) {
                group = new SubscriptionGroup(name);
                group.subscriptionUrl = entry.url;
                map.put(name, group);
                subscriptionGroups.add(group);
            }
        }
        for (SharedConfig.ProxyInfo info : subscriptionProxyList) {
            String name = getSubscriptionGroupTitle(info);
            SubscriptionGroup group = map.get(name);
            if (group == null) {
                group = new SubscriptionGroup(name);
                map.put(name, group);
                subscriptionGroups.add(group);
            }
            group.proxies.add(info);
        }
        ArrayList<SubscriptionGroup> pinnedGroups = new ArrayList<>();
        ArrayList<SubscriptionGroup> normalGroups = new ArrayList<>();
        for (SubscriptionGroup group : subscriptionGroups) {
            XraySubscriptionStore.Entry entry = getEntryForGroup(group);
            if (entry != null && entry.pinned) {
                pinnedGroups.add(group);
            } else {
                normalGroups.add(group);
            }
        }
        subscriptionGroups.clear();
        subscriptionGroups.addAll(pinnedGroups);
        subscriptionGroups.addAll(normalGroups);
        for (SubscriptionGroup group : subscriptionGroups) {
            sortSubscriptionGroupProxies(group);
            subscriptionRows.add(SubscriptionRow.forGroup(group));
            if (!collapsedSubscriptions.contains(group.name)) {
                for (SharedConfig.ProxyInfo proxy : group.proxies) {
                    subscriptionRows.add(SubscriptionRow.forProxy(proxy));
                }
            }
        }
        canCollapseSubscriptions = true;
        if (subscriptionGroups.isEmpty()) {
            canCollapseSubscriptions = false;
        }
        Iterator<String> iterator = collapsedSubscriptions.iterator();
        while (iterator.hasNext()) {
            if (!map.containsKey(iterator.next())) {
                iterator.remove();
            }
        }
    }

    private String getSubscriptionGroupTitle(SharedConfig.ProxyInfo info) {
        if (info != null && !TextUtils.isEmpty(info.subscriptionName)) {
            return info.subscriptionName;
        }
        return getString(R.string.ProxySubscriptionEmpty);
    }

    private boolean isSubscriptionGroupPosition(int position) {
        if (subscriptionStartRow == -1) {
            return false;
        }
        SubscriptionRow row = getSubscriptionRow(position);
        return row != null && row.isGroup;
    }

    private SubscriptionRow getSubscriptionRow(int position) {
        if (subscriptionStartRow == -1 || position < subscriptionStartRow || position >= subscriptionEndRow) {
            return null;
        }
        int index = position - subscriptionStartRow;
        if (index < 0 || index >= subscriptionRows.size()) {
            return null;
        }
        return subscriptionRows.get(index);
    }

    private boolean isProxyPosition(int position) {
        return getProxyInfoByPosition(position) != null;
    }

    private SharedConfig.ProxyInfo getProxyInfoByPosition(int position) {
        if (position >= proxyStartRow && position < proxyEndRow) {
            SubscriptionRow row = getSubscriptionRow(position);
            if (row != null) {
                return row.isGroup ? null : row.proxy;
            }
            if (manualStartRow != -1 && position >= manualStartRow && position < manualEndRow) {
                int index = position - manualStartRow;
                if (index >= 0 && index < manualProxyList.size()) {
                    return manualProxyList.get(index);
                }
            }
        }
        return null;
    }

    private int getPositionForProxy(SharedConfig.ProxyInfo proxy) {
        if (subscriptionStartRow != -1) {
            for (int i = 0; i < subscriptionRows.size(); i++) {
                SubscriptionRow row = subscriptionRows.get(i);
                if (!row.isGroup && row.proxy == proxy) {
                    return subscriptionStartRow + i;
                }
            }
        }
        int index = manualProxyList.indexOf(proxy);
        if (index >= 0 && manualStartRow != -1) {
            return manualStartRow + index;
        }
        return -1;
    }

    private boolean isProxyVisibleForCheck(SharedConfig.ProxyInfo proxy) {
        if (subscriptionStartRow == -1 || !collapsedSubscriptions.isEmpty()) {
            return getPositionForProxy(proxy) != -1;
        }
        return true;
    }

    private void notifyProxyRangesChanged() {
        if (listAdapter == null) {
            return;
        }
        if (subscriptionStartRow != -1) {
            listAdapter.notifyItemRangeChanged(subscriptionStartRow, subscriptionEndRow - subscriptionStartRow);
        }
        if (manualStartRow != -1) {
            listAdapter.notifyItemRangeChanged(manualStartRow, manualEndRow - manualStartRow);
        }
    }

    private void updateVisibleProxyStatuses() {
        for (int position = proxyStartRow; position < proxyEndRow; position++) {
            if (!isProxyPosition(position)) {
                continue;
            }
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof TextDetailProxyCell) {
                ((TextDetailProxyCell) holder.itemView).updateStatus();
            }
        }
    }

    private void updateVisibleProxySelection(SharedConfig.ProxyInfo info) {
        for (int position = proxyStartRow; position < proxyEndRow; position++) {
            SharedConfig.ProxyInfo rowInfo = getProxyInfoByPosition(position);
            if (rowInfo == null) {
                continue;
            }
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof TextDetailProxyCell) {
                TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                cell.setChecked(rowInfo == info);
                cell.updateStatus();
            }
        }
    }

    private void updateHwidMenuState() {
        if (hwidModeItem != null) {
            hwidModeItem.setChecked(ProxyUtil.isHwidModeEnabled());
        }
    }

    private void deleteSubscriptionGroup(SubscriptionGroup group) {
        if (group == null) {
            return;
        }
        String title = group.name;
        ProxyUtil.removeSubscriptionsByTitle(title);
        XraySubscriptionStore.removeByDisplayTitle(title);
        XraySubscriptionWorkScheduler.syncAll();
        for (SharedConfig.ProxyInfo info : new ArrayList<>(SharedConfig.getProxyList())) {
            if (info.isSubscription && TextUtils.equals(getSubscriptionGroupTitle(info), title)) {
                SharedConfig.deleteProxy(info);
            }
        }
        collapsedSubscriptions.remove(title);
        if (SharedConfig.currentProxy == null) {
            useProxyForCalls = false;
            useProxySettings = false;
        }
        NotificationCenter.getGlobalInstance().removeObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
        NotificationCenter.getGlobalInstance().addObserver(ProxyListActivity.this, NotificationCenter.proxySettingsChanged);
        updateRows(true);
        if (listAdapter != null) {
            if (SharedConfig.currentProxy == null) {
                listAdapter.notifyItemChanged(useProxyRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
                listAdapter.notifyItemChanged(callsRow, ListAdapter.PAYLOAD_CHECKED_CHANGED);
            }
            listAdapter.clearSelected();
        }
    }

    private void showSubscriptionGroupMenu(SubscriptionGroup group, View anchor) {
        if (group == null) {
            return;
        }
        XraySubscriptionStore.Entry entry = getEntryForGroup(group);
        String sortMode = entry != null && !TextUtils.isEmpty(entry.sortMode) ? entry.sortMode : SORT_ADDED;
        String subUrl = entry != null ? entry.url : group.subscriptionUrl;
        ItemOptions options = ItemOptions.makeOptions(this, anchor);
        options.add(R.drawable.msg_copy, getString(R.string.CopySubscriptionLink), () -> {
            if (!TextUtils.isEmpty(subUrl)) {
                AndroidUtilities.addToClipboard(subUrl);
            }
        });
        options.add(R.drawable.msg_qrcode, getString(R.string.ShareQRCode), () -> {
            if (!TextUtils.isEmpty(subUrl) && getParentActivity() != null) {
                NekoProxyUtil.showQrDialog(getParentActivity(), subUrl);
            }
        });
        options.add(R.drawable.msg_edit, getString(R.string.EditSubscription), () -> showEditSubscriptionDialog(group, entry));
        options.add(R.drawable.msg_recent, getString(R.string.UpdateSchedule), () -> showUpdateScheduleDialog(group, entry));
        options.add(R.drawable.msg_pin, getString(R.string.PinToTop), () -> {
            if (entry == null) {
                return;
            }
            entry.pinned = !entry.pinned;
            XraySubscriptionStore.save(entry);
            updateRows(true);
        });
        options.add(R.drawable.msg_delete, getString(R.string.Delete), () -> confirmDeleteSubscriptionGroup(group, getPositionForGroup(group)));
        options.addGap();
        options.addChecked(SORT_ADDED.equals(sortMode), getString(R.string.ProxySubscriptionSortAdded), () -> setSubscriptionSortMode(group, SORT_ADDED));
        options.addChecked(SORT_PING.equals(sortMode), getString(R.string.ProxySubscriptionSortPing), () -> setSubscriptionSortMode(group, SORT_PING));
        options.addChecked(SORT_NAME.equals(sortMode), getString(R.string.ProxySubscriptionSortName), () -> setSubscriptionSortMode(group, SORT_NAME));
        options.show();
    }

    private void setSubscriptionSortMode(SubscriptionGroup group, String mode) {
        XraySubscriptionStore.Entry entry = getEntryForGroup(group);
        if (entry == null) {
            return;
        }
        entry.sortMode = mode;
        XraySubscriptionStore.save(entry);
        updateRows(true);
    }

    private void showEditSubscriptionDialog(SubscriptionGroup group, XraySubscriptionStore.Entry entry) {
        if (getParentActivity() == null) {
            return;
        }
        if (entry == null) {
            entry = getEntryForGroup(group);
        }
        if (entry == null) {
            return;
        }
        final XraySubscriptionStore.Entry entryFinal = entry;
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));

        OutlineTextContainerView nameOutline = new OutlineTextContainerView(getParentActivity());
        nameOutline.setText(getString(R.string.SubscriptionName));
        nameOutline.animateSelection(1f, false);
        EditTextBoldCursor nameField = createOutlinedField(nameOutline, entryFinal.remark);
        container.addView(nameOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));

        OutlineTextContainerView urlOutline = new OutlineTextContainerView(getParentActivity());
        urlOutline.setText(getString(R.string.ProxySubscriptionUrl));
        urlOutline.animateSelection(1f, false);
        EditTextBoldCursor urlField = createOutlinedField(urlOutline, entryFinal.url);
        container.addView(urlOutline, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.EditSubscription));
        builder.setView(container);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.OK), (dialog, which) -> {
            String newRemark = nameField.getText().toString().trim();
            String newUrl = XraySubscriptionStore.normalizeUrl(urlField.getText().toString().trim());
            if (TextUtils.isEmpty(newUrl)) {
                return;
            }
            String oldUrl = entryFinal.url;
            entryFinal.remark = newRemark;
            entryFinal.url = newUrl;
            if (!TextUtils.equals(oldUrl, newUrl)) {
                XraySubscriptionStore.removeByUrl(oldUrl);
            }
            XraySubscriptionStore.save(entryFinal);
            ProxyUtil.refreshSubscriptionsByTitle(getParentActivity(), entryFinal.getDisplayTitle());
            XraySubscriptionWorkScheduler.syncAll();
            updateRows(true);
        });
        showDialog(builder.create());
    }

    private void showUpdateScheduleDialog(SubscriptionGroup group, XraySubscriptionStore.Entry entry) {
        if (getParentActivity() == null) {
            return;
        }
        if (entry == null) {
            entry = getEntryForGroup(group);
        }
        if (entry == null) {
            return;
        }
        final XraySubscriptionStore.Entry entryFinal = entry;
        LinearLayout container = new LinearLayout(getParentActivity());
        container.setOrientation(LinearLayout.VERTICAL);

        TextCheckCell autoUpdateCell = new TextCheckCell(getParentActivity());
        autoUpdateCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        autoUpdateCell.setTextAndValueAndCheck(getString(R.string.ProxySubscriptionAutomaticUpdates), getString(R.string.ProxySubscriptionAutomaticUpdatesInfo), entryFinal.autoUpdate, true, false);
        container.addView(autoUpdateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        TextView intervalLabel = new TextView(getParentActivity());
        intervalLabel.setText(getString(R.string.ProxySubscriptionUpdateIntervalLabel));
        intervalLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        intervalLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        intervalLabel.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(4));
        container.addView(intervalLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int initialIndex = entryFinal.updateIntervalMinutes == 360 ? 0 : entryFinal.updateIntervalMinutes == 720 ? 1 : 2;
        final int[] selectedMinutes = new int[]{entryFinal.updateIntervalMinutes > 0 ? entryFinal.updateIntervalMinutes : 1440};
        SlideChooseView intervalChoose = new SlideChooseView(getParentActivity());
        intervalChoose.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        intervalChoose.setOptions(initialIndex, "6 h", "12 h", "24 h");
        intervalChoose.setCallback(index -> {
            if (index == 0) {
                selectedMinutes[0] = 360;
            } else if (index == 1) {
                selectedMinutes[0] = 720;
            } else {
                selectedMinutes[0] = 1440;
            }
        });
        container.addView(intervalChoose, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));

        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.UpdateSchedule));
        builder.setView(container);
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setPositiveButton(getString(R.string.Confirm), (dialog, which) -> {
            entryFinal.autoUpdate = autoUpdateCell.isChecked();
            entryFinal.updateIntervalMinutes = selectedMinutes[0];
            XraySubscriptionStore.save(entryFinal);
            XraySubscriptionWorkScheduler.syncAll();
            updateRows(true);
        });
        showDialog(builder.create());
    }

    private EditTextBoldCursor createOutlinedField(OutlineTextContainerView outline, String initial) {
        EditTextBoldCursor field = new EditTextBoldCursor(getParentActivity());
        field.setSingleLine(true);
        field.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        field.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        field.setCursorColor(Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated));
        field.setBackground(null);
        field.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(14), AndroidUtilities.dp(16), AndroidUtilities.dp(14));
        if (!TextUtils.isEmpty(initial)) {
            field.setText(initial);
        }
        outline.attachEditText(field);
        field.setOnFocusChangeListener((v, hasFocus) -> outline.animateSelection(hasFocus || field.length() > 0 ? 1f : 0f, hasFocus || field.length() > 0));
        outline.animateSelection(!TextUtils.isEmpty(initial) ? 1f : 0f, !TextUtils.isEmpty(initial));
        outline.addView(field, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        return field;
    }

    private View buildScheduleSection(boolean autoUpdate, int intervalMinutes, TextCheckCell[] autoUpdateCellOut, int[] selectedMinutesOut) {
        LinearLayout section = new LinearLayout(getParentActivity());
        section.setOrientation(LinearLayout.VERTICAL);
        TextCheckCell autoUpdateCell = new TextCheckCell(getParentActivity());
        autoUpdateCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        autoUpdateCell.setTextAndValueAndCheck(getString(R.string.ProxySubscriptionAutomaticUpdates), getString(R.string.ProxySubscriptionAutomaticUpdatesInfo), autoUpdate, true, false);
        section.addView(autoUpdateCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        if (autoUpdateCellOut != null && autoUpdateCellOut.length > 0) {
            autoUpdateCellOut[0] = autoUpdateCell;
        }

        TextView intervalLabel = new TextView(getParentActivity());
        intervalLabel.setText(getString(R.string.ProxySubscriptionUpdateIntervalLabel));
        intervalLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        intervalLabel.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        intervalLabel.setPadding(AndroidUtilities.dp(21), AndroidUtilities.dp(12), AndroidUtilities.dp(21), AndroidUtilities.dp(4));
        section.addView(intervalLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        int initialIndex = intervalMinutes == 360 ? 0 : intervalMinutes == 720 ? 1 : 2;
        selectedMinutesOut[0] = intervalMinutes > 0 ? intervalMinutes : 1440;
        SlideChooseView intervalChoose = new SlideChooseView(getParentActivity());
        intervalChoose.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        intervalChoose.setOptions(initialIndex, "6 h", "12 h", "24 h");
        intervalChoose.setCallback(index -> {
            if (index == 0) {
                selectedMinutesOut[0] = 360;
            } else if (index == 1) {
                selectedMinutesOut[0] = 720;
            } else {
                selectedMinutesOut[0] = 1440;
            }
        });
        section.addView(intervalChoose, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        return section;
    }

    private void confirmDeleteSubscriptionGroup(SubscriptionGroup group, int swipedPosition) {
        if (group == null) {
            updateRows(true);
            return;
        }
        if (getParentActivity() == null) {
            deleteSubscriptionGroup(group);
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(getString(R.string.DeleteSubscriptionConfirm));
        builder.setMessage(group.name);
        builder.setPositiveButton(getString(R.string.Delete), (dialog, which) -> deleteSubscriptionGroup(group));
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.setOnDismissListener(dialog -> {
            if (listAdapter != null) {
                listAdapter.notifyItemChanged(swipedPosition);
            }
        });
        AlertDialog dialog = builder.create();
        showDialog(dialog);
        TextView button = (TextView) dialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold));
        }
    }

    private void checkProxyList(boolean force) {
        for (int a = 0, count = proxyList.size(); a < count; a++) {
            final SharedConfig.ProxyInfo proxyInfo = proxyList.get(a);
            if (proxyInfo.unrecognized) {
                continue;
            }
            if (!isProxyVisibleForCheck(proxyInfo) && proxyInfo != SharedConfig.currentProxy) {
                continue;
            }
            if (force && proxyInfo.checking) {
                proxyInfo.checking = false;
            }
            if (proxyInfo.checking || SystemClock.elapsedRealtime() - proxyInfo.availableCheckTime < 2 * 60 * 1000 && !force) {
                continue;
            }
            proxyInfo.checking = true;
            ConnectionsManager.getInstance(currentAccount).checkProxy(proxyInfo.settings, time -> AndroidUtilities.runOnUIThread(() -> {
                proxyInfo.availableCheckTime = SystemClock.elapsedRealtime();
                proxyInfo.checking = false;
                if (time == -1) {
                    proxyInfo.available = false;
                    proxyInfo.ping = 0;
                } else {
                    proxyInfo.ping = time;
                    proxyInfo.available = true;
                }
                NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxyCheckDone, proxyInfo);
            }));
        }
    }

    private void checkProxyList() {
        checkProxyList(false);
    }

    @Override
    protected void onDialogDismiss(Dialog dialog) {
        DownloadController.getInstance(currentAccount).checkAutodownloadSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        scheduleSubscriptionMetaRefresh();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private void scheduleSubscriptionMetaRefresh() {
        if (subscriptionMetaRefreshRunnable == null) {
            subscriptionMetaRefreshRunnable = () -> {
                refreshVisibleSubscriptionMeta();
                scheduleSubscriptionMetaRefresh();
            };
        }
        AndroidUtilities.cancelRunOnUIThread(subscriptionMetaRefreshRunnable);
        AndroidUtilities.runOnUIThread(subscriptionMetaRefreshRunnable, SUBSCRIPTION_META_REFRESH_MS);
    }

    private void refreshVisibleSubscriptionMeta() {
        if (listView == null || subscriptionStartRow == -1) {
            return;
        }
        for (int position = subscriptionStartRow; position < subscriptionEndRow; position++) {
            SubscriptionRow row = getSubscriptionRow(position);
            if (row == null || !row.isGroup || row.group == null) {
                continue;
            }
            RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(position);
            if (holder != null && holder.itemView instanceof SubscriptionGroupCell) {
                ((SubscriptionGroupCell) holder.itemView).updateMetaRow(row.group, getEntryForGroup(row.group));
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.proxyChangedByRotation) {
            listView.forAllChild(view -> {
                RecyclerView.ViewHolder holder = listView.getChildViewHolder(view);
                if (holder.itemView instanceof TextDetailProxyCell) {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    cell.setChecked(cell.currentInfo == SharedConfig.currentProxy);
                    cell.updateStatus();
                }
            });

            updateRows(false);
        } else if (id == NotificationCenter.proxySettingsChanged) {
            updateRows(true);
        } else if (id == NotificationCenter.didUpdateConnectionState) {
            int state = ConnectionsManager.getInstance(account).getConnectionState();
            if (currentConnectionState != state) {
                currentConnectionState = state;
                if (listView != null && SharedConfig.currentProxy != null) {
                    int proxyPosition = getPositionForProxy(SharedConfig.currentProxy);
                    if (proxyPosition >= 0) {
                        RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(proxyPosition);
                        if (holder != null) {
                            TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                            cell.updateStatus();
                        }
                    }

                    if (currentConnectionState == ConnectionsManager.ConnectionStateConnected) {
                        updateRows(true);
                    }
                }
            }
        } else if (id == NotificationCenter.proxyCheckDone) {
            if (listView != null) {
                SharedConfig.ProxyInfo proxyInfo = (SharedConfig.ProxyInfo) args[0];
                int proxyPosition = getPositionForProxy(proxyInfo);
                if (proxyPosition >= 0) {
                    RecyclerListView.Holder holder = (RecyclerListView.Holder) listView.findViewHolderForAdapterPosition(proxyPosition);
                    if (holder != null) {
                        TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                        cell.updateStatus();
                    }
                }

                boolean checking = false;
                if (!wasCheckedAllList) {
                    for (SharedConfig.ProxyInfo info : proxyList) {
                        if (info.checking || info.availableCheckTime == 0) {
                            checking = true;
                            break;
                        }
                    }
                    if (!checking) {
                        wasCheckedAllList = true;
                    }
                }
                if (!checking) {
                    updateRows(true);
                }
            }
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final static int VIEW_TYPE_SHADOW = 0,
            VIEW_TYPE_TEXT_SETTING = 1,
            VIEW_TYPE_HEADER = 2,
            VIEW_TYPE_TEXT_CHECK = 3,
            VIEW_TYPE_INFO = 4,
            VIEW_TYPE_PROXY_DETAIL = 5,
            VIEW_TYPE_SLIDE_CHOOSER = 6,
            VIEW_TYPE_SUBSCRIPTION_GROUP = 7;

        public static final int PAYLOAD_CHECKED_CHANGED = 0;
        public static final int PAYLOAD_SELECTION_CHANGED = 1;
        public static final int PAYLOAD_SELECTION_MODE_CHANGED = 2;

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;

            setHasStableIds(true);
        }

        public void toggleSelected(int position) {
            if (!isProxyPosition(position)) {
                return;
            }
            SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
            if (info == null) {
                return;
            }
            if (selectedItems.contains(info)) {
                selectedItems.remove(info);
            } else {
                selectedItems.add(info);
            }
            notifyItemChanged(position, PAYLOAD_SELECTION_CHANGED);
            checkActionMode();
        }

        public void clearSelected() {
            selectedItems.clear();
            notifyProxyRangesChanged();
            checkActionMode();
        }

        private void checkActionMode() {
            int selectedCount = selectedItems.size();
            boolean actionModeShowed = actionBar.isActionModeShowed();
            if (selectedCount > 0) {
                selectedCountTextView.setNumber(selectedCount, actionModeShowed);
                if (!actionModeShowed) {
                    actionBar.showActionMode();
                    notifyProxyRangesChanged();
                }
            } else if (actionModeShowed) {
                actionBar.hideActionMode();
                notifyItemRangeChanged(proxyStartRow, proxyEndRow - proxyStartRow, PAYLOAD_SELECTION_MODE_CHANGED);
            }
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case VIEW_TYPE_SHADOW: {
                    break;
                }
                case VIEW_TYPE_TEXT_SETTING: {
                    TextSettingsCell textCell = (TextSettingsCell) holder.itemView;
                    textCell.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                    if (position == proxyAddRow) {
                        textCell.setText(getString(R.string.AddProxy), deleteAllRow != -1);
                    } else if (position == deleteAllRow) {
                        textCell.setTextColor(Theme.getColor(Theme.key_text_RedRegular));
                        textCell.setText(getString(R.string.DeleteAllProxies), false);
                    }
                    break;
                }
                case VIEW_TYPE_HEADER: {
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == connectionsHeaderRow) {
                        headerCell.setText(getString(R.string.ProxyConnections));
                    } else if (position == subscriptionHeaderRow) {
                        headerCell.setText(getString(R.string.ProxyCategorySubscriptions));
                    } else if (position == manualHeaderRow) {
                        headerCell.setText(getString(R.string.ProxyCategoryManual));
                    }
                    break;
                }
                case VIEW_TYPE_TEXT_CHECK: {
                    TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                    if (position == useProxyRow) {
                        checkCell.setTextAndCheck(getString(R.string.UseProxySettings), useProxySettings, rotationRow != -1);
                    } else if (position == rotationRow) {
                        checkCell.setTextAndCheck(getString(R.string.UseProxyRotation), SharedConfig.proxyRotationEnabled, true);
                    } else if (position == callsRow) {
                        checkCell.setTextAndCheck(getString(R.string.UseProxyForCalls), useProxyForCalls, false);
                    }
                    break;
                }
                case VIEW_TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == rotationTimeoutInfoRow) {
                        cell.setText(getString(R.string.ProxyRotationTimeoutInfo));
                    } else if (position == callsDetailRow) {
                        cell.setText(getString(R.string.UseProxyForCallsInfo));
                    }
                    break;
                }
                case VIEW_TYPE_PROXY_DETAIL: {
                    TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                    SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
                    cell.setProxy(info);
                    cell.setChecked(SharedConfig.currentProxy == info);
                    cell.setItemSelected(selectedItems.contains(info), false);
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), false);
                    break;
                }
                case VIEW_TYPE_SUBSCRIPTION_GROUP: {
                    SubscriptionGroupCell cell = (SubscriptionGroupCell) holder.itemView;
                    SubscriptionRow row = getSubscriptionRow(position);
                    if (row != null && row.group != null) {
                        cell.bind(row.group, collapsedSubscriptions.contains(row.group.name), canCollapseSubscriptions);
                    }
                    break;
                }
                case VIEW_TYPE_SLIDE_CHOOSER: {
                    if (position == rotationTimeoutRow) {
                        SlideChooseView chooseView = (SlideChooseView) holder.itemView;
                        ArrayList<Integer> options = new ArrayList<>(ProxyRotationController.ROTATION_TIMEOUTS);
                        String[] values = new String[options.size()];
                        for (int i = 0; i < options.size(); i++) {
                            values[i] = LocaleController.formatString(R.string.ProxyRotationTimeoutSeconds, options.get(i));
                        }
                        chooseView.setCallback(i -> {
                            SharedConfig.proxyRotationTimeout = i;
                            SharedConfig.saveConfig();
                        });
                        chooseView.setOptions(SharedConfig.proxyRotationTimeout, values);
                    }
                    break;
                }
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
            if (holder.getItemViewType() == VIEW_TYPE_PROXY_DETAIL && !payloads.isEmpty()) {
                TextDetailProxyCell cell = (TextDetailProxyCell) holder.itemView;
                SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
                if (payloads.contains(PAYLOAD_SELECTION_CHANGED)) {
                    cell.setItemSelected(selectedItems.contains(info), true);
                }
                if (payloads.contains(PAYLOAD_SELECTION_MODE_CHANGED)) {
                    cell.setSelectionEnabled(!selectedItems.isEmpty(), true);
                }
            } else if (holder.getItemViewType() == VIEW_TYPE_TEXT_CHECK && payloads.contains(PAYLOAD_CHECKED_CHANGED)) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                } else if (position == callsRow) {
                    checkCell.setChecked(useProxyForCalls);
                } else if (position == callsRow) {
                    checkCell.setChecked(useProxyForCalls);
                }
            } else {
                super.onBindViewHolder(holder, position, payloads);
            }
        }

        @Override
        public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
            int viewType = holder.getItemViewType();
            if (viewType == VIEW_TYPE_TEXT_CHECK) {
                TextCheckCell checkCell = (TextCheckCell) holder.itemView;
                int position = holder.getAdapterPosition();
                if (position == useProxyRow) {
                    checkCell.setChecked(useProxySettings);
                } else if (position == rotationRow) {
                    checkCell.setChecked(SharedConfig.proxyRotationEnabled);
                } else if (position == callsRow) {
                    checkCell.setChecked(useProxyForCalls);
                }
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (isProxyPosition(position)) {
                SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
                return info != null && !info.unrecognized;
            }
            return position == useProxyRow || position == rotationRow || position == callsRow || position == proxyAddRow || position == subscriptionUserAgentRow || position == deleteAllRow;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case VIEW_TYPE_SHADOW:
                    view = new ShadowSectionCell(mContext);
                    break;
                case VIEW_TYPE_TEXT_SETTING:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_TEXT_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case VIEW_TYPE_SLIDE_CHOOSER:
                    view = new SlideChooseView(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case VIEW_TYPE_SUBSCRIPTION_GROUP: {
                    SubscriptionGroupCell groupCell = new SubscriptionGroupCell(mContext);
                    groupCell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    groupCell.setOnRefreshClickListener(v -> {
                        int pos = listView.getChildAdapterPosition(groupCell);
                        SubscriptionRow row = getSubscriptionRow(pos);
                        if (row != null && row.isGroup && row.group != null) {
                            ProxyUtil.refreshSubscriptionsByTitle(getParentActivity(), row.group.name);
                        }
                    });
                    groupCell.setOnCollapseClickListener(v -> {
                        if (!canCollapseSubscriptions || !selectedItems.isEmpty()) {
                            return;
                        }
                        int pos = listView.getChildAdapterPosition(groupCell);
                        SubscriptionRow row = getSubscriptionRow(pos);
                        if (row == null || row.group == null) {
                            return;
                        }
                        if (collapsedSubscriptions.contains(row.group.name)) {
                            collapsedSubscriptions.remove(row.group.name);
                        } else {
                            collapsedSubscriptions.add(row.group.name);
                        }
                        updateRows(true);
                    });
                    view = groupCell;
                    break;
                }
                case VIEW_TYPE_PROXY_DETAIL:
                default:
                    view = new TextDetailProxyCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public long getItemId(int position) {
            // Random stable ids, could be anything non-repeating
            if (position == useProxyShadowRow) {
                return -1;
            } else if (position == proxyShadowRow) {
                return -2;
            } else if (position == proxyAddRow) {
                return -3;
            } else if (position == useProxyRow) {
                return -4;
            } else if (position == callsRow) {
                return -5;
            } else if (position == connectionsHeaderRow) {
                return -6;
            } else if (position == subscriptionHeaderRow) {
                return -12;
            } else if (position == manualHeaderRow) {
                return -13;
            } else if (isSubscriptionGroupPosition(position)) {
                SubscriptionRow row = getSubscriptionRow(position);
                if (row != null && row.group != null) {
                    return (0x7fL << 32) ^ (row.group.name.hashCode() & 0xffffffffL);
                }
                return -14;
            } else if (position == deleteAllRow) {
                return -8;
            } else if (position == rotationRow) {
                return -9;
            } else if (position == rotationTimeoutRow) {
                return -10;
            } else if (position == rotationTimeoutInfoRow) {
                return -11;
            } else if (isProxyPosition(position)) {
                SharedConfig.ProxyInfo info = getProxyInfoByPosition(position);
                return info != null ? info.hashCode() : -7;
            } else {
                return -7;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == useProxyShadowRow || position == proxyShadowRow) {
                return VIEW_TYPE_SHADOW;
            } else if (position == proxyAddRow || position == deleteAllRow || position == subscriptionUserAgentRow) {
                return VIEW_TYPE_TEXT_SETTING;
            } else if (position == useProxyRow || position == rotationRow || position == callsRow) {
                return VIEW_TYPE_TEXT_CHECK;
            } else if (position == connectionsHeaderRow || position == subscriptionHeaderRow || position == manualHeaderRow) {
                return VIEW_TYPE_HEADER;
            } else if (position == rotationTimeoutRow) {
                return VIEW_TYPE_SLIDE_CHOOSER;
            } else if (isSubscriptionGroupPosition(position)) {
                return VIEW_TYPE_SUBSCRIPTION_GROUP;
            } else if (isProxyPosition(position)) {
                return VIEW_TYPE_PROXY_DETAIL;
            } else {
                return VIEW_TYPE_INFO;
            }
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, TextCheckCell.class, HeaderCell.class, TextDetailProxyCell.class, SubscriptionGroupCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextDetailProxyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText6));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGreenText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG | ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_text_RedRegular));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{TextDetailProxyCell.class}, new String[]{"checkImageView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{SubscriptionGroupCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SubscriptionGroupCell.class}, new String[]{"refreshView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_IMAGECOLOR, new Class[]{SubscriptionGroupCell.class}, new String[]{"collapseView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayIcon));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText2));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        return themeDescriptions;
    }
}
