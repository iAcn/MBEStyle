package me.iacn.mbestyle.ui.fragment;

import android.app.ProgressDialog;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.CheckBox;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import me.iacn.mbestyle.R;
import me.iacn.mbestyle.bean.RequestBean;
import me.iacn.mbestyle.network.LeanApi;
import me.iacn.mbestyle.presenter.RequestPresenter;
import me.iacn.mbestyle.ui.activity.MainActivity;
import me.iacn.mbestyle.ui.adapter.RequestAdapter;
import me.iacn.mbestyle.ui.callback.OnItemClickListener;
import me.iacn.mbestyle.ui.callback.OnItemLongClickListener;
import me.iacn.mbestyle.util.SharedPrefUtils;
import me.iacn.mbestyle.util.StringUtils;

/**
 * Created by iAcn on 2017/2/18
 * Email i@iacn.me
 */

public class RequestFragment extends ILazyFragment implements OnItemClickListener, View.OnClickListener, OnItemLongClickListener {

    private SwipeRefreshLayout srMain;
    private RecyclerView rvApp;
    private FloatingActionButton mFab;

    private RequestPresenter mPresenter;
    private List<RequestBean> mApps;
    private List<Integer> mCheckedPositions;
    private RequestAdapter mAdapter;

    private MainActivity mActivity;

    @Override
    protected int getContentView() {
        return R.layout.fragment_request;
    }

    @Override
    protected void findView() {
        srMain = (SwipeRefreshLayout) findViewById(R.id.sr_main);
        rvApp = (RecyclerView) findViewById(R.id.rv_app);
        mFab = (FloatingActionButton) findViewById(R.id.fab);
    }

    @Override
    protected void setListener() {
        rvApp.setLayoutManager(new LinearLayoutManager(getActivity()));
        // 优化同大小 Item 的性能
        rvApp.setHasFixedSize(true);
        // 确保每个 Item 都会走 onBindViewHolder()
        rvApp.setItemViewCacheSize(0);

        mFab.setOnClickListener(this);

        srMain.setColorSchemeResources(R.color.colorAccent);
        srMain.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                for (RequestBean bean : mApps) {
                    // 使所有 Item 会重新加载
                    bean.total = -1;
                }

                mAdapter.notifyItemRangeChanged(0, mAdapter.getItemCount());
            }
        });
    }

    @Override
    protected void initData() {
        mPresenter = new RequestPresenter(this);
        mPresenter.loadInstallApp();

        mActivity = (MainActivity) getActivity();
    }

    @Override
    protected boolean isDataComplete() {
        return mApps != null;
    }

    @Override
    public void onItemClick(View itemView, int position) {
        RequestBean bean = mApps.get(position);
        CheckBox cbCheck = (CheckBox) itemView.findViewById(R.id.cb_check);

        // 反向选择
        cbCheck.setChecked(!bean.isCheck);
        bean.isCheck = !bean.isCheck;

        if (bean.isCheck) {
            mCheckedPositions.add(position);
        } else {
            mCheckedPositions.remove(Integer.valueOf(position));
        }

        handleFabShow();
    }

    @Override
    public boolean onItemLongClick(View itemView, int position) {
        RequestBean bean = mApps.get(position);
        String template = "ComponentInfo{$mainIntentActivity$}"
                .replace("$mainIntentActivity$", bean.activity);

        StringUtils.copyToClipboard(getActivity(), template);
        Toast.makeText(getActivity(), R.string.toast_copy_component_info, Toast.LENGTH_SHORT).show();

        return true;
    }

    public void onLoadData(List<RequestBean> list) {
        super.onLoadData();

        mApps = list;
        mCheckedPositions = new ArrayList<>();

        mAdapter = new RequestAdapter(mApps, mPresenter);
        mAdapter.setOnItemClickListener(this);
        mAdapter.setOnItemLongClickListener(this);
        rvApp.setAdapter(mAdapter);
    }

    @Override
    public void onClick(View v) {
        List<RequestBean> newRequests = new ArrayList<>();
        List<Integer> copyList = new ArrayList<>(mCheckedPositions);

        long currentTime = System.currentTimeMillis();
        long twelveHours = 43200000;

        for (int i : copyList) {
            RequestBean bean = mApps.get(i);
            long lastRequestTime = SharedPrefUtils.getLong(getActivity(), bean.packageName, 0);

            if (lastRequestTime + twelveHours < currentTime) {
                // 12 个小时内同一个应用不得申请第二次
                newRequests.add(bean);
            } else {
                mCheckedPositions.remove(Integer.valueOf(i));
            }
        }

        LeanApi.getInstance().postRequests(newRequests).subscribe(new Observer<Boolean>() {

            private ProgressDialog mProgressDialog;

            @Override
            public void onSubscribe(Disposable d) {
                mProgressDialog = new ProgressDialog(getActivity());
                mProgressDialog.setMessage(getString(R.string.waiting));
                mProgressDialog.setCancelable(false);
                mProgressDialog.show();
            }

            @Override
            public void onNext(Boolean success) {
                Toast.makeText(getActivity(), success ? R.string.request_success : R.string.request_failure,
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(Throwable e) {
                Toast.makeText(getActivity(), R.string.occur_error + e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onComplete() {
                long currentTime = System.currentTimeMillis();

                for (int i : mCheckedPositions) {
                    // 使能重新加载当前应用
                    RequestBean bean = mApps.get(i);
                    bean.total = -1;

                    // 存储单个应用上次申请的时间
                    SharedPrefUtils.putLong(getActivity(), bean.packageName, currentTime);

                    mAdapter.notifyItemChanged(i);
                }

                deselectAll();
                mProgressDialog.dismiss();
            }
        });
    }

    public void onBackPressed() {
        if (mCheckedPositions.size() > 0) {
            deselectAll();
        } else {
            getActivity().finish();
        }
    }

    public void stopLoadingState() {
        srMain.setRefreshing(false);
    }

    /**
     * 处理是否显示 Fab
     */
    private void handleFabShow() {
        if (mCheckedPositions.size() > 0) {
            if (!mFab.isShown())
                mFab.show();

            mActivity.setToolbarTitle(String.format(
                    Locale.getDefault(), getString(R.string.check_sum), mCheckedPositions.size()));

        } else if (mFab.isShown()) {
            mFab.hide();
            mActivity.setToolbarTitle(getString(R.string.app_title));
            mCheckedPositions.clear();
        }
    }

    private void deselectAll() {
        mCheckedPositions.clear();
        handleFabShow();

        // 取消所有 Bean 内记录的选中状态
        for (RequestBean bean : mApps) {
            bean.isCheck = false;
        }

        // 当前可见的所有 Item 取消选择
        for (int i = 0; i < rvApp.getChildCount(); i++) {
            View childAt = rvApp.getChildAt(i);
            CheckBox cbCheck = (CheckBox) childAt.findViewById(R.id.cb_check);
            cbCheck.setChecked(false);
        }
    }
}