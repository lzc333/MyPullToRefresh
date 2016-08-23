package com.liuzhcheng.mypulltorefresh;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;


public class MyRefreshView extends LinearLayout implements View.OnTouchListener {
    private static final String TAG = "MyRefreshView";
    public static final int STATUS_PULL_TO_REFRESH = 0;//下拉状态
    public static final int STATUS_RELEASE_TO_REFRESH = 1;//释放立即刷新状态
    public static final int STATUS_REFRESHING = 2;//正在刷新状态
    public static final int STATUS_REFRESH_FINISHED = 3;//刷新完成或未刷新状态
    //header下拉的最大的topMargin，效果是下拉到一定程度就下拉不了
    private static final int MAX_TOP_MARGIN=80;

    private PullToRefreshListener mListener;//回调接口
    private View header;//下拉头的View
    private ListView listView;
    private ImageView arrow;
    private ImageView wait;
    private TextView description;//文字描述

    private MarginLayoutParams headerLayoutParams;//下拉头的布局参数
    private int hideHeaderHeight;//下拉头的高度

    //当前状态，可选值有STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH,
    //STATUS_REFRESHING 和 STATUS_REFRESH_FINISHED
    private int currentStatus = STATUS_REFRESH_FINISHED;


    private float yDown;//手指按下时的屏幕纵坐标
    private float yMove;//手指移动时的屏幕纵坐标
    private int touchSlop;//系统所能识别的被认为是滑动的最小距离
    private int top_Margin;//记录header的headerLayoutParams.topMargin



    public MyRefreshView(Context context) {
        super(context);
        init(context);
    }

    public MyRefreshView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        hideHeaderHeight = header.getHeight();//得到下拉头View的高度
        headerLayoutParams = (MarginLayoutParams) header.getLayoutParams();
        //让其LayoutParams.topMargin为负的下拉头高度，这样刚开始时，下拉头就会隐藏在屏幕上方
        headerLayoutParams.topMargin = -hideHeaderHeight;
        listView = (ListView) getChildAt(1);//得到ListView
        listView.setOnTouchListener(this);//设置onTouch监听，来处理下拉的具体逻辑
    }

    private void init(Context context) {
        header = LayoutInflater.from(context).inflate(R.layout.layout_myhead, null, true);
        wait = (ImageView) header.findViewById(R.id.wait);
        arrow = (ImageView) header.findViewById(R.id.arrow);
        description = (TextView) header.findViewById(R.id.description);
        //系统所能识别的被认为是滑动的最小距离
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setOrientation(VERTICAL);
        addView(header, 0);
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (IsAbleToPull()) {//判断listView滑到顶部
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    yMove = event.getRawY();
                    int distance = (int) (yMove - yDown);
                    //distance>0说明手指是向下滑动(下拉)，distance>touchSlop说明这次为有效下拉操作
                    if (distance > touchSlop) {
                        if (currentStatus != STATUS_REFRESHING) {
                            // 通过偏移下拉头的topMargin值，来实现下拉效果
                            //distance/2是为了有更好的下拉体验，你得向下滑动header高度的两倍，才能使header刚好全部显示
                            headerLayoutParams.topMargin = (distance / 2) - hideHeaderHeight;
                            if (headerLayoutParams.topMargin > MAX_TOP_MARGIN) {
                                headerLayoutParams.topMargin = MAX_TOP_MARGIN;//下拉到一定程度就下拉不了了
                            }
                            header.setLayoutParams(headerLayoutParams);

                            if (headerLayoutParams.topMargin > 0) {//当header全部显示出来时，箭头上指，释放立即刷新
                                if (currentStatus != STATUS_RELEASE_TO_REFRESH) {//加个判断是为了当headerLayoutParams.topMargin>0
                                    headerInfoChange(STATUS_RELEASE_TO_REFRESH);//的所有下拉过程中只执行一次
                                }
                                currentStatus = STATUS_RELEASE_TO_REFRESH;
                            } else {//当header没有全部显示时，箭头下指，下拉可刷新
                                if (currentStatus != STATUS_PULL_TO_REFRESH) {//加个判断是为了当headerLayoutParams.topMargin<0
                                    headerInfoChange(STATUS_PULL_TO_REFRESH);//的所有下拉过程中只执行一次
                                }
                                currentStatus = STATUS_PULL_TO_REFRESH;
                            }
                        }
                    }
                    break;

                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                        headerInfoChange(STATUS_REFRESHING);//arrow隐藏，wait可见，正在刷新
                        new RefreshingTask().execute();// 松手时如果是释放立即刷新状态，就调用正在刷新的任务
                    } else if (currentStatus == STATUS_PULL_TO_REFRESH) {
                        hideHeader();// 松手时如果是下拉状态，就隐藏下拉头
                    }
                    break;
            }

            if (currentStatus == STATUS_PULL_TO_REFRESH || currentStatus == STATUS_RELEASE_TO_REFRESH) {
                // 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
                listView.setPressed(false);
                //Set whether this view can receive the focus. Setting this to false will also ensure that this view is not focusable in touch mode
                listView.setFocusable(false);
                listView.setFocusableInTouchMode(false);
                // 当前正处于下拉或释放状态，通过返回true屏蔽掉ListView的滚动事件
                return true;
            }

        }
        return false;
    }


    class RefreshingTask extends AsyncTask<Void, Integer, Void> {

        @Override
        protected Void doInBackground(Void... params) {
            int topMargin = headerLayoutParams.topMargin;
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            currentStatus = STATUS_REFRESHING;
            if (mListener != null) {
                mListener.onRefresh();
            }
            publishProgress(topMargin);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            hideHeader();
            currentStatus = STATUS_REFRESH_FINISHED;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            headerLayoutParams.topMargin = values[0];
        }

    }

    public void hideHeader() {
        top_Margin = headerLayoutParams.topMargin;//先记录下header上移的初始位置
        final int height =top_Margin + hideHeaderHeight;//header从开始上移到结束上移的总高度
        ValueAnimator valueAnimator = ValueAnimator.ofFloat(0f, 1f);
        valueAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                //valueAnimator.getAnimatedValue()从0一>1变化，当为0时，表示开始上移，这时headerLayoutParams.topMargin
                //应该为之前保存的top_Margin，当为1时，表示结束上移，这时headerLayoutParams.topMargin应该为负的hideHeaderHeight
                headerLayoutParams.topMargin = top_Margin - (int) ((float) valueAnimator.getAnimatedValue() * height);
                header.setLayoutParams(headerLayoutParams);

            }
        });
        valueAnimator.setDuration(1000);
        valueAnimator.start();
        currentStatus=STATUS_REFRESH_FINISHED;
    }

    public void headerInfoChange(int i) {
        ObjectAnimator anim;
        switch (i) {
            case STATUS_PULL_TO_REFRESH:
                description.setText("下拉可刷新");
                wait.setVisibility(GONE);
                arrow.setVisibility(VISIBLE);
                anim = ObjectAnimator.ofFloat(arrow, "rotation", 180, 0);
                anim.setDuration(300).start();
                break;
            case STATUS_RELEASE_TO_REFRESH:
                description.setText("释放立即刷新");
                wait.setVisibility(GONE);
                arrow.setVisibility(VISIBLE);
                anim = ObjectAnimator.ofFloat(arrow, "rotation", 0, 180);
                anim.setDuration(300).start();
                break;
            case STATUS_REFRESHING:
                wait.setVisibility(VISIBLE);
                description.setText("正在刷新");
                arrow.setVisibility(INVISIBLE);
                break;
        }
    }

    /**
     * 根据当前ListView的滚动状态来设定  ableToPull
     * 的值，每次都需要在onTouch中第一个执行，这样可以判断出当前应该是滚动ListView，还是应该进行下拉。
     */
    private boolean IsAbleToPull() {
        View firstChild = listView.getChildAt(0);//得到listView的第一个item
        if (firstChild != null) {
            int firstVisiblePos = listView.getFirstVisiblePosition();
            if (firstVisiblePos == 0 && firstChild.getTop() == 0) {
                //如果可视的第一个Item的position为0，说明当前的第一个Item为整个listView的第一个Item,并且
                // 第一个Item的上边缘距离父布局值为0，两者同时满足说明ListView滚动到了最顶部，此时允许下拉刷新
                return true;
            } else {
                if (headerLayoutParams.topMargin != -hideHeaderHeight) {
                    headerLayoutParams.topMargin =- hideHeaderHeight;
                    header.setLayoutParams(headerLayoutParams);
                }
                return false;
            }
        } else {
            return true;// 如果ListView中没有元素，默认允许下拉刷新
        }
    }


    /**
     * 下拉刷新的监听器，使用下拉刷新的地方应该注册此监听器来获取刷新回调。
     *
     * @author guolin
     */
    public interface PullToRefreshListener {

        /**
         * 刷新时会去回调此方法，在方法内编写具体的刷新逻辑。注意此方法是在子线程中调用的， 你可以不必另开线程来进行耗时操作。
         */
        void onRefresh();

    }

    public void setOnRefreshListener(PullToRefreshListener listener) {
        mListener = listener;

    }

}
