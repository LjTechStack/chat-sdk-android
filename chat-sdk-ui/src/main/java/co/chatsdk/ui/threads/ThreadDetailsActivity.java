/*
 * Created by Itzik Braun on 12/3/2015.
 * Copyright (c) 2015 deluge. All rights reserved.
 *
 * Last Modification at: 3/12/15 4:27 PM
 */

package co.chatsdk.ui.threads;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.annotation.LayoutRes;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;

import co.chatsdk.core.dao.Keys;
import co.chatsdk.core.dao.Thread;
import co.chatsdk.core.events.NetworkEvent;
import co.chatsdk.core.interfaces.ThreadType;
import co.chatsdk.core.session.ChatSDK;
import co.chatsdk.core.utils.StringChecker;
import co.chatsdk.core.utils.Strings;
import co.chatsdk.ui.R;
import co.chatsdk.ui.users.ThreadUsersFragment;
import co.chatsdk.ui.utils.ImagePreviewActivity;
import co.chatsdk.ui.utils.ToastHelper;

/**
 * Created by braunster on 24/11/14.
 */
public class ThreadDetailsActivity extends ImagePreviewActivity {

    /** Set true if you want slide down animation for this context exit. */
    protected boolean animateExit = false;

    protected Thread thread;
    protected ImageView threadImageView;
    protected TextView threadNameTextView;

    protected ThreadUsersFragment usersFragment;

    protected ActionBar actionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            getDataFromBundle(savedInstanceState);
        }
        else {
            if (getIntent().getExtras() != null) {
                getDataFromBundle(getIntent().getExtras());
            }
            else {
                finish();
            }
        }
        if (thread == null) {
            ToastHelper.show(this, R.string.error_thread_not_found);
            finish();
        }

        initViews();

        // Depending on the thread type, disable / enable options
        if (thread.typeIs(ThreadType.Private1to1)) {
            threadNameTextView.setVisibility(View.INVISIBLE);
        } else {
            threadNameTextView.setVisibility(View.VISIBLE);
        }

    }

    protected @LayoutRes int activityLayout() {
        return R.layout.activity_thread_details;
    }

    protected void initViews() {

        threadImageView = findViewById(R.id.chat_sdk_thread_image_view);
        threadNameTextView = findViewById(R.id.name_text_view);

        dm.add(ChatSDK.events().sourceOnMain()
                .filter(NetworkEvent.threadDetailsUpdated())
                .filter(NetworkEvent.filterThreadEntityID(thread.getEntityID()))
                .subscribe(networkEvent -> reloadData()));

        reloadData();
    }

    protected void reloadData () {
        actionBar = getSupportActionBar();
        String name = Strings.nameForThread(thread);
        if (actionBar != null) {
            actionBar.setTitle(name);
            actionBar.setHomeButtonEnabled(true);
        }
        threadNameTextView.setText(name);

        if (!StringChecker.isNullOrEmpty(thread.getImageUrl())) {
            threadImageView.setOnClickListener(v -> zoomImageFromThumbnail(threadImageView, thread.getImageUrl()));
            Picasso.get().load(thread.getImageUrl()).into(threadImageView);
        } else {
            ThreadImageBuilder.load(threadImageView, thread);
            threadImageView.setOnClickListener(null);
        }

        // CoreThread users bundle
        if (usersFragment == null) {
            usersFragment = new ThreadUsersFragment(thread);
            getSupportFragmentManager().beginTransaction().replace(R.id.frame_thread_users, usersFragment).commit();
        } else {
            usersFragment.loadData(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        reloadData();
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        setResult(Activity.RESULT_OK);

        finish(); // Finish needs to be called before animate exit
        if (animateExit) {
            overridePendingTransition(R.anim.dummy, R.anim.slide_top_bottom_out);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // TODO: Enable thread images
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        getDataFromBundle(intent.getExtras());
    }

    protected void getDataFromBundle(Bundle bundle) {
        if (bundle == null) {
            return;
        }

        animateExit = bundle.getBoolean(Keys.IntentKeyAnimateExit, animateExit);

        String threadEntityID = bundle.getString(Keys.IntentKeyThreadEntityID);

        if (threadEntityID != null && !threadEntityID.isEmpty()) {
            thread = ChatSDK.db().fetchThreadWithEntityID(threadEntityID);
        }
        else {
            finish();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(Keys.IntentKeyThreadEntityID, thread.getEntityID());
        outState.putBoolean(Keys.IntentKeyAnimateExit, animateExit);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.threads_menu, menu);

        // Only the creator can modify the group. Also, private 1-to-1 chats can't be edited
        if (!thread.getCreatorEntityId().equals(ChatSDK.currentUserID()) || thread.typeIs(ThreadType.Private1to1)) {
            menu.removeItem(R.id.action_edit);
        }

        if (!ChatSDK.thread().muteEnabled(thread)) {
            menu.removeItem(R.id.action_mute);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        if (item.getItemId() == R.id.action_edit) {
            ChatSDK.ui().startThreadEditDetailsActivity(ChatSDK.shared().context(), thread.getEntityID());
        }
        if (item.getItemId() == R.id.action_mute) {
            if (thread.metaValueForKey(Keys.Mute) != null) {
                ChatSDK.thread().unmute(thread).subscribe(ChatSDK.shared().getCrashReporter());
            } else {
                ChatSDK.thread().mute(thread).subscribe(ChatSDK.shared().getCrashReporter());
            }
            invalidateOptionsMenu();
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem item = menu.findItem(R.id.action_mute);

        if (item != null) {
            String muteText = getApplicationContext().getString(R.string.mute_notifications);
            String unmuteText = getApplicationContext().getString(R.string.unmute_notifications);

            if (thread.metaValueForKey(Keys.Mute) != null) {
                item.setTitle(unmuteText);
            } else {
                item.setTitle(muteText);
            }
        }

        return super.onPrepareOptionsMenu(menu);
    }

}
