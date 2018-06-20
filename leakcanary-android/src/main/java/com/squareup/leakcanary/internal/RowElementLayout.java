package com.squareup.leakcanary.internal;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import com.squareup.leakcanary.CanaryLog;
import com.squareup.leakcanary.R;

public class RowElementLayout extends ViewGroup {

  private final int connectorWidth;
  private final int rowMargins;
  private final int moreSize;
  private final int minHeight;
  private final int titleMarginTop;
  private final int moreMarginTop;

  private View connector;
  private View moreButton;
  private View title;
  private View details;

  public RowElementLayout(Context context, AttributeSet attrs) {
    super(context, attrs);
    Resources resources = getResources();
    connectorWidth = resources.getDimensionPixelSize(R.dimen.leak_canary_connector_width);
    rowMargins = resources.getDimensionPixelSize(R.dimen.leak_canary_row_margins);
    moreSize = resources.getDimensionPixelSize(R.dimen.leak_canary_more_size);
    minHeight = resources.getDimensionPixelSize(R.dimen.leak_canary_row_min);
    titleMarginTop = resources.getDimensionPixelSize(R.dimen.leak_canary_row_title_margin_top);
    moreMarginTop = resources.getDimensionPixelSize(R.dimen.leak_canary_more_margin_top);
  }

  @Override protected void onFinishInflate() {
    super.onFinishInflate();
    connector = findViewById(R.id.leak_canary_row_connector);
    moreButton = findViewById(R.id.leak_canary_row_more);
    title = findViewById(R.id.leak_canary_row_title);
    details = findViewById(R.id.leak_canary_row_details);
  }

  @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
    int availableWidth = MeasureSpec.getSize(widthMeasureSpec);
    int titleWidth = availableWidth - connectorWidth - moreSize - 4 * rowMargins;
    int titleWidthSpec = MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.AT_MOST);
    int titleHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    title.measure(titleWidthSpec, titleHeightSpec);

    int moreSizeSpec = MeasureSpec.makeMeasureSpec(moreSize, MeasureSpec.EXACTLY);
    moreButton.measure(moreSizeSpec, moreSizeSpec);

    int totalHeight = titleMarginTop + title.getMeasuredHeight();

    int detailsWidth = availableWidth - connectorWidth - 2 * rowMargins;
    int detailsWidthSpec = MeasureSpec.makeMeasureSpec(detailsWidth, MeasureSpec.AT_MOST);
    int detailsHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
    details.measure(detailsWidthSpec, detailsHeightSpec);
    if (details.getVisibility() != GONE) {
      totalHeight += details.getMeasuredHeight();
    }
    totalHeight = Math.max(totalHeight, minHeight);

    int connectorWidthSpec = MeasureSpec.makeMeasureSpec(connectorWidth, MeasureSpec.EXACTLY);
    int connectorHeightSpec = MeasureSpec.makeMeasureSpec(totalHeight, MeasureSpec.EXACTLY);

    connector.measure(connectorWidthSpec, connectorHeightSpec);
    setMeasuredDimension(availableWidth, totalHeight);
  }

  @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
    int connectorRight = l + rowMargins + connector.getMeasuredWidth();
    connector.layout(rowMargins, l, connectorRight, t + connector.getMeasuredHeight());

    moreButton.layout(r - rowMargins - moreSize, moreMarginTop, r - rowMargins,
        moreMarginTop + moreSize);

    int titleLeft = connectorRight + rowMargins;
    int titleBottom = titleMarginTop + title.getMeasuredHeight();
    title.layout(titleLeft, titleMarginTop, titleLeft + title.getMeasuredWidth(), titleBottom);

    if (details.getVisibility() != GONE) {
      details.layout(titleLeft, titleBottom, r, b);
    }
  }
}
