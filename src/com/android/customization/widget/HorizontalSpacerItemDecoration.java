package com.android.customization.widget;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.Dimension;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ItemDecoration;
import androidx.recyclerview.widget.RecyclerView.State;

/**
 * RecyclerView ItemDecorator that adds a horizontal space of the given size between items
 * (except the first and last)
 */
public class HorizontalSpacerItemDecoration extends ItemDecoration {

    private final int mOffset;
    private final int mEndOffset;

    public HorizontalSpacerItemDecoration(@Dimension int offset, @Dimension int endOffset) {
        mOffset = offset;
        mEndOffset = endOffset;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
            @NonNull RecyclerView parent, @NonNull State state) {
        int position = parent.getChildAdapterPosition(view);
        int left = position == 0 ? mEndOffset : mOffset;
        int right = (position == parent.getAdapter().getItemCount() - 1) ? mEndOffset : mOffset;
        outRect.set(left, 0, right, 0);
    }
}
