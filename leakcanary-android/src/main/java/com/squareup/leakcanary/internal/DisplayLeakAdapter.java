/*
 * Copyright (C) 2015 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.squareup.leakcanary.internal;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import com.squareup.leakcanary.CanaryLog;
import com.squareup.leakcanary.Exclusion;
import com.squareup.leakcanary.LeakTrace;
import com.squareup.leakcanary.LeakTraceElement;
import com.squareup.leakcanary.R;
import com.squareup.leakcanary.Reachability;

import static com.squareup.leakcanary.LeakTraceElement.Type.STATIC_FIELD;
import static java.lang.Integer.toHexString;

final class DisplayLeakAdapter extends BaseAdapter {

  private static final int TOP_ROW = 0;
  private static final int NORMAL_ROW = 1;

  private boolean[] opened = new boolean[0];

  private LeakTrace leakTrace = null;
  private String referenceKey;
  private String referenceName = "";

  private final String classNameColorHexString;
  private final String leakColorHexString;
  private final String referenceColorHexString;
  private final String extraColorHexString;
  private final int leakBackgroundColor;

  public DisplayLeakAdapter(Resources resources) {
    classNameColorHexString = hexStringColor(resources, R.color.leak_canary_class_name);
    leakColorHexString = hexStringColor(resources, R.color.leak_canary_leak);
    referenceColorHexString = hexStringColor(resources, R.color.leak_canary_reference);
    extraColorHexString = hexStringColor(resources, R.color.leak_canary_extra);
    leakBackgroundColor = resources.getColor(R.color.leak_canary_leak_background);
  }

  // https://stackoverflow.com/a/6540378/703646
  private static String hexStringColor(Resources resources, @ColorRes int colorResId) {
    return String.format("#%06X", (0xFFFFFF & resources.getColor(colorResId)));
  }

  @Override public View getView(int position, View convertView, ViewGroup parent) {
    Context context = parent.getContext();
    if (getItemViewType(position) == TOP_ROW) {
      if (convertView == null) {
        convertView =
            LayoutInflater.from(context).inflate(R.layout.leak_canary_ref_top_row, parent, false);
      }
      TextView textView = findById(convertView, R.id.leak_canary_row_text);
      textView.setText(context.getPackageName());
    } else {
      if (convertView == null) {
        convertView =
            LayoutInflater.from(context).inflate(R.layout.leak_canary_ref_row, parent, false);
      }
      findById(convertView, R.id.leak_canary_row_layout).requestLayout();

      TextView titleView = findById(convertView, R.id.leak_canary_row_title);

      boolean isRoot = position == 1;
      boolean isLeakingInstance = position == getCount() - 1;
      LeakTraceElement element = getItem(position);

      Reachability reachability = leakTrace.expectedReachability.get(element);
      boolean maybeLeakCause;
      if (isLeakingInstance || reachability == Reachability.UNREACHABLE) {
        maybeLeakCause = false;
      } else {
        LeakTraceElement nextElement = getItem(position + 1);
        Reachability nextReachability = leakTrace.expectedReachability.get(nextElement);
        maybeLeakCause = nextReachability != Reachability.REACHABLE;
      }

      Spanned htmlSpanned =
          elementToHtmlString(element, isRoot, opened[position], maybeLeakCause, isLeakingInstance,
              convertView.getResources());
      titleView.setText(htmlSpanned);

      DisplayLeakConnectorView connector = findById(convertView, R.id.leak_canary_row_connector);
      connector.setType(getConnectorType(position));
      MoreDetailsView moreDetailsView = findById(convertView, R.id.leak_canary_row_more);
      moreDetailsView.setOpened(opened[position]);
    }

    return convertView;
  }

  @NonNull private DisplayLeakConnectorView.Type getConnectorType(int position) {
    boolean isRoot = position == 1;
    if (isRoot) {
      LeakTraceElement nextElement = getItem(position + 1);
      Reachability nextReachability = leakTrace.expectedReachability.get(nextElement);
      if (nextReachability != Reachability.REACHABLE) {
        return DisplayLeakConnectorView.Type.START_LAST_REACHABLE;
      }
      return DisplayLeakConnectorView.Type.START;
    } else {
      boolean isLeakingInstance = position == getCount() - 1;
      if (isLeakingInstance) {
        LeakTraceElement previousElement = getItem(position - 1);
        Reachability previousReachability = leakTrace.expectedReachability.get(previousElement);
        if (previousReachability != Reachability.UNREACHABLE) {
          return DisplayLeakConnectorView.Type.END_FIRST_UNREACHABLE;
        }
        return DisplayLeakConnectorView.Type.END;
      } else {
        LeakTraceElement element = getItem(position);
        Reachability reachability = leakTrace.expectedReachability.get(element);
        switch (reachability) {
          case UNKNOWN:
            return DisplayLeakConnectorView.Type.NODE_UNKNOWN;
          case REACHABLE:
            LeakTraceElement nextElement = getItem(position + 1);
            Reachability nextReachability = leakTrace.expectedReachability.get(nextElement);
            if (nextReachability != Reachability.REACHABLE) {
              return DisplayLeakConnectorView.Type.NODE_LAST_REACHABLE;
            } else {
              return DisplayLeakConnectorView.Type.NODE_REACHABLE;
            }
          case UNREACHABLE:
            LeakTraceElement previousElement = getItem(position - 1);
            Reachability previousReachability = leakTrace.expectedReachability.get(previousElement);
            if (previousReachability != Reachability.UNREACHABLE) {
              return DisplayLeakConnectorView.Type.NODE_FIRST_UNREACHABLE;
            } else {
              return DisplayLeakConnectorView.Type.NODE_UNREACHABLE;
            }
          default:
            throw new IllegalStateException("Unknown value: " + reachability);
        }
      }
    }
  }

  private Spanned elementToHtmlString(LeakTraceElement element, boolean root, boolean opened,
      boolean maybeLeakCause, boolean isLeakingInstance, Resources resources) {
    String htmlString = "";

    int separator = element.className.lastIndexOf('.');
    String qualifier;
    String simpleName;
    if (separator == -1) {
      qualifier = "";
      simpleName = element.className;
    } else {
      qualifier = element.className.substring(0, separator + 1);
      simpleName = element.className.substring(separator + 1);
    }
    simpleName = simpleName.replace("[]", "[ ]");

    String styledClassName =
        "<font color='" + classNameColorHexString + "'>" + simpleName + "</font>";

    if (element.referenceName != null) {
      String referenceName = element.referenceName.replaceAll("<", "&lt;")
          .replaceAll(">", "&gt;");

      if (maybeLeakCause) {
        referenceName =
            "<u><font color='" + leakColorHexString + "'>" + referenceName + "</font></u>";
      } else {
        referenceName =
            "<font color='" + referenceColorHexString + "'>" + referenceName + "</font>";
      }

      if (element.type == STATIC_FIELD) {
        referenceName = "<i>" + referenceName + "</i>";
      }

      String classAndReference = styledClassName + "." + referenceName;

      if (maybeLeakCause) {
        classAndReference = "<b>" + classAndReference + "</b>";
      }

      htmlString += classAndReference;
    } else {
      htmlString += styledClassName;
    }

    if (opened && element.extra != null) {
      htmlString += " <font color='" + extraColorHexString + "'>" + element.extra + "</font>";
    }

    Exclusion exclusion = element.exclusion;
    if (exclusion != null) {
      if (opened) {
        htmlString += "<br/><br/>Excluded by rule";
        if (exclusion.name != null) {
          htmlString += " <font color='#ffffff'>" + exclusion.name + "</font>";
        }
        htmlString += " matching <font color='#f3cf83'>" + exclusion.matching + "</font>";
        if (exclusion.reason != null) {
          htmlString += " because <font color='#f3cf83'>" + exclusion.reason + "</font>";
        }
      } else {
        htmlString += " (excluded)";
      }
    }

    if (opened) {
      // TODO build it nicer
      htmlString += "<br>"
          + "<font color='" + extraColorHexString + "'>"
          + element.toDetailedString().replace("\n", "<br>")
          + "</font>";
    }

    if (isLeakingInstance && !referenceName.equals("") && opened) {
      htmlString += " <font color='" + extraColorHexString + "'>" + referenceName + "</font>";
    }

    SpannableStringBuilder builder = (SpannableStringBuilder) Html.fromHtml(htmlString);
    if (maybeLeakCause) {
      //StyleSpan[] styleSpans = builder.getSpans(0, builder.length(), StyleSpan.class);
      StyleSpan[] styleSpans = {};
      for (StyleSpan span : styleSpans) {
        if (span.getStyle() == Typeface.BOLD) {
          int start = builder.getSpanStart(span);
          int end = builder.getSpanEnd(span);
          builder.removeSpan(span);
          builder.setSpan(new BackgroundColorSpan(leakBackgroundColor), start, end, 0);
        }
      }

      UnderlineSpan[] underlineSpans = builder.getSpans(0, builder.length(), UnderlineSpan.class);
      for (UnderlineSpan span : underlineSpans) {
        int start = builder.getSpanStart(span);
        int end = builder.getSpanEnd(span);
        builder.removeSpan(span);
        builder.setSpan(new SquigglySpan(resources), start, end, 0);
      }
    }

    return builder;
  }

  public void update(LeakTrace leakTrace, String referenceKey, String referenceName) {
    if (referenceKey.equals(this.referenceKey)) {
      // Same data, nothing to change.
      return;
    }
    this.referenceKey = referenceKey;
    this.referenceName = referenceName;
    this.leakTrace = leakTrace;
    opened = new boolean[1 + leakTrace.elements.size()];
    notifyDataSetChanged();
  }

  public void toggleRow(int position) {
    opened[position] = !opened[position];
    notifyDataSetChanged();
  }

  @Override public int getCount() {
    if (leakTrace == null) {
      return 1;
    }
    return 1 + leakTrace.elements.size();
  }

  @Override public LeakTraceElement getItem(int position) {
    if (getItemViewType(position) == TOP_ROW) {
      return null;
    }
    return leakTrace.elements.get(position - 1);
  }

  @Override public int getViewTypeCount() {
    return 2;
  }

  @Override public int getItemViewType(int position) {
    if (position == 0) {
      return TOP_ROW;
    }
    return NORMAL_ROW;
  }

  @Override public long getItemId(int position) {
    return position;
  }

  @SuppressWarnings({ "unchecked", "TypeParameterUnusedInFormals" })
  private static <T extends View> T findById(View view, int id) {
    return (T) view.findViewById(id);
  }
}
