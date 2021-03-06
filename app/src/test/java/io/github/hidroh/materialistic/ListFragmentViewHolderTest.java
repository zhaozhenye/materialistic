package io.github.hidroh.materialistic;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.ShadowContentResolverCompatJellybean;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.view.View;
import android.view.animation.Animation;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.internal.ShadowExtractor;
import org.robolectric.shadows.ShadowContentObserver;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPopupMenu;
import org.robolectric.shadows.ShadowToast;
import org.robolectric.util.ActivityController;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Named;

import io.github.hidroh.materialistic.accounts.UserServices;
import io.github.hidroh.materialistic.data.FavoriteManager;
import io.github.hidroh.materialistic.data.HackerNewsClient;
import io.github.hidroh.materialistic.data.Item;
import io.github.hidroh.materialistic.data.ItemManager;
import io.github.hidroh.materialistic.data.MaterialisticProvider;
import io.github.hidroh.materialistic.data.ResponseListener;
import io.github.hidroh.materialistic.data.SessionManager;
import io.github.hidroh.materialistic.data.TestHnItem;
import io.github.hidroh.materialistic.data.WebItem;
import io.github.hidroh.materialistic.test.ListActivity;
import io.github.hidroh.materialistic.test.ShadowAnimation;
import io.github.hidroh.materialistic.test.ShadowItemTouchHelper;
import io.github.hidroh.materialistic.test.ShadowLinearLayoutManager;
import io.github.hidroh.materialistic.test.ShadowRecyclerView;
import io.github.hidroh.materialistic.test.ShadowRecyclerViewAdapter;
import io.github.hidroh.materialistic.test.ShadowSupportPreferenceManager;
import io.github.hidroh.materialistic.test.ShadowSwipeRefreshLayout;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.assertj.android.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.robolectric.Shadows.shadowOf;

@Config(shadows = {ShadowSwipeRefreshLayout.class,
        ShadowSupportPreferenceManager.class,
        ShadowRecyclerView.class,
        ShadowLinearLayoutManager.class,
        ShadowItemTouchHelper.class,
        ShadowRecyclerViewAdapter.class,
        ShadowRecyclerViewAdapter.ShadowViewHolder.class,
        ShadowAnimation.class,
        ShadowContentResolverCompatJellybean.class})
@RunWith(RobolectricGradleTestRunner.class)
public class ListFragmentViewHolderTest {
    private ActivityController<ListActivity> controller;
    private ShadowRecyclerViewAdapter adapter;
    private ListActivity activity;
    private TestHnItem item;
    @Inject SessionManager sessionManager;
    @Inject @Named(ActivityModule.HN) ItemManager itemManager;
    @Inject FavoriteManager favoriteManager;
    @Inject UserServices userServices;
    @Captor ArgumentCaptor<ResponseListener<Item[]>> storiesListener;
    @Captor ArgumentCaptor<ResponseListener<Item>> itemListener;
    @Captor ArgumentCaptor<UserServices.Callback> voteCallback;
    private RecyclerView recyclerView;
    private ItemTouchHelper.SimpleCallback swipeCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TestApplication.applicationGraph.inject(this);
        reset(sessionManager);
        reset(favoriteManager);
        reset(itemManager);
        reset(userServices);
        item = new TestHnItem(1) {
            @Override
            public int getRank() {
                return 46;
            }

            @Override
            public String getBy() {
                return "author";
            }
        };
        controller = Robolectric.buildActivity(ListActivity.class)
                .create().start().resume().visible();
        activity = controller.get();
        Bundle args = new Bundle();
        args.putString(ListFragment.EXTRA_ITEM_MANAGER, HackerNewsClient.class.getName());
        args.putString(ListFragment.EXTRA_FILTER, ItemManager.TOP_FETCH_MODE);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content,
                        Fragment.instantiate(activity, ListFragment.class.getName(), args))
                .commit();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_DEFAULT),
                storiesListener.capture());
        storiesListener.getValue().onResponse(new Item[]{item});
        recyclerView = (RecyclerView) activity.findViewById(R.id.recycler_view);
        swipeCallback = (ItemTouchHelper.SimpleCallback)
                ((ShadowRecyclerView) ShadowExtractor.extract(recyclerView))
                        .getItemTouchHelperCallback();
        adapter = (ShadowRecyclerViewAdapter) ShadowExtractor.extract(recyclerView.getAdapter());
        adapter.makeItemVisible(0);
        item.populate(new PopulatedStory(1));
    }

    @Test
    public void testStory() {
        ContentValues cv = new ContentValues();
        cv.put("itemid", "1");
        shadowOf(activity.getContentResolver())
                .insert(MaterialisticProvider.URI_VIEWED, cv);
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat(holder.itemView.findViewById(R.id.bookmarked)).isNotVisible();
        assertNotViewed();
        assertThat((TextView) holder.itemView.findViewById(R.id.rank)).hasTextString("46");
        assertThat((TextView) holder.itemView.findViewById(R.id.title)).hasTextString("title");
        assertThat((TextView) holder.itemView.findViewById(R.id.comment))
                .isVisible()
                .isEmpty();
        assertViewed();
    }

    @Test
    public void testNewStory() {
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_NETWORK),
                storiesListener.capture());
        storiesListener.getValue().onResponse(new Item[]{new TestHnItem(2) {
            @Override
            public int getRank() {
                return 46;
            }
        }});
        activity.findViewById(R.id.snackbar_action).performClick();
        verify(itemManager, atLeastOnce()).getItem(anyString(),
                eq(ItemManager.MODE_NETWORK),
                itemListener.capture());
        itemListener.getValue().onResponse(new PopulatedStory(2));
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.rank)).hasTextString("46*");
    }

    @Test
    public void testPromoted() {
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_NETWORK),
                storiesListener.capture());
        storiesListener.getValue().onResponse(new Item[]{new TestHnItem(1) {
            @Override
            public int getRank() {
                return 45;
            }
        }});
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_NETWORK), itemListener.capture());
        itemListener.getValue().onResponse(new PopulatedStory(1));
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.rank)).containsText("+1");
    }

    @Test
    public void testNewComments() {
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_NETWORK),
                storiesListener.capture());
        storiesListener.getValue().onResponse(new Item[]{new TestHnItem(1)});
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_NETWORK), itemListener.capture());
        itemListener.getValue().onResponse(new PopulatedStory(1) {
            @Override
            public int getDescendants() {
                return 2;
            }

            @Override
            public long[] getKids() {
                return new long[]{2, 3};
            }
        });
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.comment)).hasTextString("2*");
    }

    @Test
    public void testPreferenceChange() {
        reset(itemManager);
        ShadowSwipeRefreshLayout shadowSwipeRefreshLayout = (ShadowSwipeRefreshLayout)
                ShadowExtractor.extract(activity.findViewById(R.id.swipe_layout));
        shadowSwipeRefreshLayout.getOnRefreshListener().onRefresh();
        verify(itemManager).getStories(anyString(),
                eq(ItemManager.MODE_NETWORK),
                storiesListener.capture());
        storiesListener.getValue().onResponse(new Item[]{new TestHnItem(2) {
            @Override
            public int getRank() {
                return 46;
            }
        }});
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_NETWORK), itemListener.capture());
        itemListener.getValue().onResponse(new PopulatedStory(2));
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.rank)).hasTextString("46*");
        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_highlight_updated), false)
                .apply();
        holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.rank)).hasTextString("46");
    }

    @Test
    public void testComment() {
        item.populate(new PopulatedStory(1) {
            @Override
            public int getDescendants() {
                return 1;
            }

            @Override
            public long[] getKids() {
                return new long[]{2};
            }
        });
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        View commentButton = holder.itemView.findViewById(R.id.comment);
        assertThat(commentButton).isVisible();
        reset(activity.multiPaneListener);
        commentButton.performClick();
        verify(activity.multiPaneListener, never()).onItemSelected(any(WebItem.class)
        );
        Intent actual = shadowOf(activity).getNextStartedActivity();
        assertEquals(ItemActivity.class.getName(), actual.getComponent().getClassName());
        assertThat(actual).hasExtra(ItemActivity.EXTRA_OPEN_COMMENTS, true);
        assertViewed();
    }

    @Test
    public void testJob() {
        item.populate(new PopulatedStory(1) {
            @Override
            public String getRawType() {
                return JOB_TYPE;
            }
        });
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.source)).isEmpty();
    }

    @Test
    public void testPoll() {
        item.populate(new PopulatedStory(1) {
            @Override
            public String getRawType() {
                return POLL_TYPE;
            }
        });
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.source)).isEmpty();
    }

    @Test
    public void testItemClick() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performClick();
        assertViewed();
        verify(activity.multiPaneListener).onItemSelected(any(WebItem.class)
        );
    }

    @Test
    public void testViewedObserver() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        assertNotViewed();
        controller.pause();
        ShadowContentObserver observer = shadowOf(shadowOf(activity
                .getContentResolver())
                .getContentObservers(MaterialisticProvider.URI_VIEWED)
                .iterator()
                .next());
        observer.dispatchChange(false, MaterialisticProvider.URI_VIEWED
                .buildUpon().appendPath("2").build()); // not in view
        observer.dispatchChange(false, MaterialisticProvider.URI_VIEWED
                    .buildUpon().appendPath("1").build()); // in view
        controller.resume();
        assertViewed();
    }

    @Test
    public void testFavoriteObserver() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        item.setFavorite(true);
        itemListener.getValue().onResponse(item);
        assertTrue(item.isFavorite());

        controller.pause();

        ShadowContentObserver observer = shadowOf(shadowOf(activity
                .getContentResolver())
                .getContentObservers(MaterialisticProvider.URI_FAVORITE)
                .iterator()
                .next());
        // observed clear
        observer.dispatchChange(false, MaterialisticProvider.URI_FAVORITE
                .buildUpon()
                .appendPath("clear")
                .build());
        adapter.makeItemVisible(0);
        RecyclerView.ViewHolder viewHolder = adapter.getViewHolder(0);
        assertFalse(item.isFavorite());
        assertThat(viewHolder.itemView.findViewById(R.id.bookmarked)).isNotVisible();
        // observed add
        observer.dispatchChange(false, MaterialisticProvider.URI_FAVORITE
                .buildUpon()
                .appendPath("add")
                .appendPath("1")
                .build());
        assertTrue(item.isFavorite());
        // observed remove
        observer.dispatchChange(false, MaterialisticProvider.URI_FAVORITE
                .buildUpon()
                .appendPath("remove")
                .appendPath("1")
                .build());
        assertFalse(item.isFavorite());

        controller.resume();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testSaveItem() {
        ShadowContentObserver observer = shadowOf(shadowOf(activity
                .getContentResolver())
                .getContentObservers(MaterialisticProvider.URI_FAVORITE)
                .iterator()
                .next());
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_save));
        verify(favoriteManager).add(any(Context.class), eq(item));
        observer.dispatchChange(false, MaterialisticProvider.URI_FAVORITE
                .buildUpon()
                .appendPath("add")
                .appendPath("1")
                .build());
        assertTrue(item.isFavorite());
        assertThat((TextView) activity.findViewById(R.id.snackbar_text))
                .isNotNull()
                .containsText(R.string.toast_saved);
        activity.findViewById(R.id.snackbar_action).performClick();
        verify(favoriteManager).remove(any(Context.class), eq("1"));
        observer.dispatchChange(false, MaterialisticProvider.URI_FAVORITE
                .buildUpon()
                .appendPath("remove")
                .appendPath("1")
                .build());
        assertFalse(item.isFavorite());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testSwipeToSaveItem() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat(swipeCallback.onMove(recyclerView, holder, holder)).isFalse();
        assertThat(swipeCallback.getSwipeThreshold(holder)).isGreaterThan(0f);
        assertThat(swipeCallback.getSwipeDirs(recyclerView, holder))
                .isEqualTo(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);

        Canvas canvas = mock(Canvas.class);
        swipeCallback.onChildDraw(canvas, recyclerView, holder, -1f, 0f,
                ItemTouchHelper.ACTION_STATE_SWIPE, true);
        verify(canvas).drawText(eq(activity.getString(R.string.save).toUpperCase()),
                anyInt(), anyInt(), any(Paint.class));

        swipeCallback.onSwiped(holder, ItemTouchHelper.LEFT);
        verify(favoriteManager).add(any(Context.class), eq(item));

        item.setFavorite(true);
        assertThat(swipeCallback.getSwipeDirs(recyclerView, holder))
                .isEqualTo(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testViewUser() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_profile));
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasComponent(activity, UserActivity.class)
                .hasExtra(UserActivity.EXTRA_USERNAME, "author");
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItem() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), eq(item.getId()), voteCallback.capture());
        voteCallback.getValue().onDone(true);
        assertEquals(activity.getString(R.string.voted), ShadowToast.getTextOfLatestToast());
        Animation animation = ((ViewSwitcher) adapter.getViewHolder(0).itemView
                .findViewById(R.id.vote_switcher))
                .getInAnimation();
        ((ShadowAnimation) ShadowExtractor.extract(animation))
                .getAnimationListener().onAnimationEnd(animation);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
        assertThat((TextView) adapter.getViewHolder(0).itemView.findViewById(R.id.score))
                .hasTextString(activity.getResources().getQuantityString(R.plurals.score, 1, 1));
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testSwipeToVoteItem() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat(swipeCallback.getSwipeDirs(recyclerView, holder))
                .isEqualTo(ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);

        Canvas canvas = mock(Canvas.class);
        swipeCallback.onChildDraw(canvas, recyclerView, holder, 1f, 0f,
                ItemTouchHelper.ACTION_STATE_SWIPE, true);
        verify(canvas).drawText(eq(activity.getString(R.string.vote_up).toUpperCase()),
                anyInt(), anyInt(), any(Paint.class));

        swipeCallback.onSwiped(holder, ItemTouchHelper.RIGHT);
        verify(userServices).voteUp(any(Context.class), eq(item.getId()), voteCallback.capture());

        item.incrementScore();
        assertThat(swipeCallback.getSwipeDirs(recyclerView, holder))
                .isEqualTo(ItemTouchHelper.LEFT);

        item.clearPendingVoted();
        assertThat(swipeCallback.getSwipeDirs(recyclerView, holder))
                .isEqualTo(ItemTouchHelper.LEFT);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItemPromptToLogin() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.findViewById(R.id.button_more).performClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), eq(item.getId()), voteCallback.capture());
        voteCallback.getValue().onDone(false);
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasComponent(activity, LoginActivity.class);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testVoteItemFailed() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_vote));
        verify(userServices).voteUp(any(Context.class), eq(item.getId()), voteCallback.capture());
        voteCallback.getValue().onError(new IOException());
        assertEquals(activity.getString(R.string.vote_failed), ShadowToast.getTextOfLatestToast());
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testReply() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_comment));
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasComponent(activity, ComposeActivity.class)
                .hasExtra(ComposeActivity.EXTRA_PARENT_ID, "1");
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Test
    public void testShare() {
        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        adapter.getViewHolder(0).itemView.performLongClick();
        PopupMenu popupMenu = ShadowPopupMenu.getLatestPopupMenu();
        assertNotNull(popupMenu);
        shadowOf(popupMenu).getOnMenuItemClickListener()
                .onMenuItemClick(new RoboMenuItem(R.id.menu_contextual_share));
        assertThat(shadowOf(activity).getNextStartedActivity())
                .hasAction(Intent.ACTION_CHOOSER);
    }

    @Test
    public void testAutoMarkAsViewed() {
        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_auto_viewed), true)
                .apply();

        ShadowRecyclerView shadowRecyclerView = (ShadowRecyclerView) ShadowExtractor.extract(recyclerView);
        ShadowLinearLayoutManager shadowLayout = (ShadowLinearLayoutManager)
                ShadowExtractor.extract(recyclerView.getLayoutManager());
        shadowLayout.setFirstVisibleItemPosition(0);
        shadowRecyclerView.getScrollListener().onScrolled(recyclerView, 0, 1);
        verify(sessionManager, never()).view(any(Context.class), anyString());

        verify(itemManager).getItem(anyString(), eq(ItemManager.MODE_DEFAULT), itemListener.capture());
        itemListener.getValue().onResponse(item);
        shadowLayout.setFirstVisibleItemPosition(0);
        shadowRecyclerView.getScrollListener().onScrolled(recyclerView, 0, 1);
        verify(sessionManager, never()).view(any(Context.class), anyString());

        shadowLayout.setFirstVisibleItemPosition(1);
        shadowRecyclerView.getScrollListener().onScrolled(recyclerView, 0, 1);
        verify(sessionManager).view(any(Context.class), anyString());

        item.setIsViewed(true);
        shadowLayout.setFirstVisibleItemPosition(1);
        shadowRecyclerView.getScrollListener().onScrolled(recyclerView, 0, 1);
        verify(sessionManager).view(any(Context.class), anyString()); // should not trigger again

        ShadowSupportPreferenceManager.getDefaultSharedPreferences(activity)
                .edit()
                .putBoolean(activity.getString(R.string.pref_auto_viewed), false)
                .apply();
        assertNull(shadowRecyclerView.getScrollListener());
    }

    @After
    public void tearDown() {
        controller.pause().stop().destroy();
    }

    private void assertViewed() {
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.title))
                .hasCurrentTextColor(ContextCompat.getColor(activity, AppUtils.getThemedResId(activity, android.R.attr.textColorSecondary)));
    }

    private void assertNotViewed() {
        RecyclerView.ViewHolder holder = adapter.getViewHolder(0);
        assertThat((TextView) holder.itemView.findViewById(R.id.title))
                .hasCurrentTextColor(ContextCompat.getColor(activity, R.color.blackT87));
    }

    @SuppressLint("ParcelCreator")
    private static class PopulatedStory extends TestHnItem {
        public PopulatedStory(long id) {
            super(id);
        }

        @Override
        public String getTitle() {
            return "title";
        }

        @Override
        public String getRawType() {
            return STORY_TYPE;
        }

        @Override
        public long[] getKids() {
            return new long[0];
        }

        @Override
        public int getDescendants() {
            return 0;
        }
    }
}
