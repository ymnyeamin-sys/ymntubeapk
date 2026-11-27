package us.shandian.giga.ui.fragment;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.nononsenseapps.filepicker.Utils;

import org.schabi.newpipe.R;
import org.schabi.newpipe.download.DownloadDialog;
import org.schabi.newpipe.download.DownloadLinkInput;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.StreamingService;
import org.schabi.newpipe.extractor.stream.StreamInfo;
import org.schabi.newpipe.settings.NewPipeSettings;
import org.schabi.newpipe.streams.io.NoFileManagerSafeGuard;
import org.schabi.newpipe.streams.io.StoredFileHelper;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.FilePickerActivityHelper;
import org.schabi.newpipe.util.ServiceHelper;

import java.io.File;
import java.io.IOException;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import us.shandian.giga.get.DownloadMission;
import us.shandian.giga.service.DownloadManager;
import us.shandian.giga.service.DownloadManagerService;
import us.shandian.giga.service.DownloadManagerService.DownloadManagerBinder;
import us.shandian.giga.ui.adapter.MissionAdapter;

public class MissionsFragment extends Fragment {

    private static final String TAG = "MissionsFragment";
    private static final int SPAN_SIZE = 2;

    private SharedPreferences mPrefs;
    private boolean mLinear;
    private MenuItem mSwitch;
    private MenuItem mClear = null;
    private MenuItem mStart = null;
    private MenuItem mPause = null;

    private RecyclerView mList;
    private View mEmpty;
    private MissionAdapter mAdapter;
    private GridLayoutManager mGridManager;
    private LinearLayoutManager mLinearManager;
    private Context mContext;

    private DownloadManagerBinder mBinder;
    private boolean mForceUpdate;
    private EditText downloadLinkInput;
    private View startDownloadFromLink;
    private final CompositeDisposable downloadDisposables = new CompositeDisposable();

    private DownloadMission unsafeMissionTarget = null;
    private final ActivityResultLauncher<Intent> requestDownloadSaveAsLauncher =
            registerForActivityResult(new StartActivityForResult(), this::requestDownloadSaveAsResult);
    private final ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            mBinder = (DownloadManagerBinder) binder;
            mBinder.clearDownloadNotifications();

            mAdapter = new MissionAdapter(mContext, mBinder.getDownloadManager(), mEmpty, getView());

            mAdapter.setRecover(MissionsFragment.this::recoverMission);

            setAdapterButtons();

            mBinder.addMissionEventListener(mAdapter);
            mBinder.enableNotifications(false);

            updateList();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // What to do?
        }


    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.missions, container, false);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(requireActivity());
        mLinear = mPrefs.getBoolean("linear", false);

        // Bind the service
        mContext.bindService(new Intent(mContext, DownloadManagerService.class), mConnection, Context.BIND_AUTO_CREATE);

        // Views
        mEmpty = v.findViewById(R.id.list_empty_view);
        mList = v.findViewById(R.id.mission_recycler);
        downloadLinkInput = v.findViewById(R.id.download_link_input);
        startDownloadFromLink = v.findViewById(R.id.start_download_from_link);
        v.findViewById(R.id.paste_download_link).setOnClickListener(view -> pasteLinkFromClipboard());
        startDownloadFromLink.setOnClickListener(view -> downloadFromLink());
        final RadioGroup downloadFilterGroup = v.findViewById(R.id.download_filter_group);
        downloadFilterGroup.setOnCheckedChangeListener((group, checkedId) ->
                applyDownloadFilter(checkedId));

        // Init layouts managers
        mGridManager = new GridLayoutManager(getActivity(), SPAN_SIZE);
        mGridManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                switch (mAdapter.getItemViewType(position)) {
                    case DownloadManager.SPECIAL_PENDING:
                    case DownloadManager.SPECIAL_FINISHED:
                        return SPAN_SIZE;
                    default:
                        return 1;
                }
            }
        });
        mLinearManager = new LinearLayoutManager(getActivity());

        setHasOptionsMenu(true);

        return v;
    }

    /**
     * Added in API level 23.
     */
    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mContext = context;
    }

    /**
     * Deprecated in API level 23, but retained for API < 23 compatibility.
     */
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        downloadDisposables.clear();
        if (mBinder == null || mAdapter == null) return;

        mBinder.removeMissionEventListener(mAdapter);
        mBinder.enableNotifications(true);
        mContext.unbindService(mConnection);
        mAdapter.onDestroy();

        mBinder = null;
        mAdapter = null;
    }

    @Override
    public void onDestroyView() {
        downloadDisposables.clear();
        downloadLinkInput = null;
        startDownloadFromLink = null;
        super.onDestroyView();
    }

    private void pasteLinkFromClipboard() {
        final ClipboardManager clipboard =
                (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
        final ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            Toast.makeText(mContext, R.string.download_link_clipboard_empty, Toast.LENGTH_SHORT)
                    .show();
            return;
        }

        final CharSequence text = clip.getItemAt(0).coerceToText(mContext);
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(mContext, R.string.download_link_clipboard_empty, Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        downloadLinkInput.setText(text);
        downloadLinkInput.setSelection(downloadLinkInput.length());
    }

    private void downloadFromLink() {
        final String input = downloadLinkInput.getText().toString();
        if (DownloadLinkInput.normalize(input) == null) {
            downloadLinkInput.setError(getString(R.string.download_link_invalid));
            return;
        }

        downloadLinkInput.setError(null);
        startDownloadFromLink.setEnabled(false);
        downloadDisposables.add(DownloadLinkInput.fetch(input, url ->
                Single.fromCallable(() -> NewPipe.getServiceByUrl(url))
                        .onErrorResumeNext(error ->
                                Single.<StreamingService>error(
                                        new DownloadLinkInput.UnsupportedDownloadLinkException(
                                                error)))
                        .flatMap(service -> {
                            if (!ServiceHelper.isSupportedServiceId(service.getServiceId())) {
                                return Single.<StreamInfo>error(
                                        new DownloadLinkInput.UnsupportedDownloadLinkException());
                            }
                            return ExtractorHelper.getStreamInfo(
                                    service.getServiceId(), url, false);
                        }))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(info -> {
                    startDownloadFromLink.setEnabled(true);
                    try {
                        new DownloadDialog(requireContext(), info)
                                .show(getParentFragmentManager(), "downloadDialog");
                    } catch (final Exception e) {
                        ErrorUtil.showSnackbar(requireActivity(), new ErrorInfo(
                                e, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Opening download dialog", info));
                    }
                }, error -> {
                    startDownloadFromLink.setEnabled(true);
                    if (error instanceof DownloadLinkInput.UnsupportedDownloadLinkException) {
                        Snackbar.make(requireView(), R.string.download_link_unsupported,
                                Snackbar.LENGTH_LONG).show();
                    } else {
                        ErrorUtil.showSnackbar(requireActivity(), new ErrorInfo(
                                error, UserAction.DOWNLOAD_OPEN_DIALOG,
                                "Resolving pasted download link"));
                    }
                }));
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        mSwitch = menu.findItem(R.id.switch_mode);
        mClear = menu.findItem(R.id.clear_list);
        mStart = menu.findItem(R.id.start_downloads);
        mPause = menu.findItem(R.id.pause_downloads);

        if (mAdapter != null) setAdapterButtons();

        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.switch_mode) {
            mLinear = !mLinear;
            updateList();
            return true;
        } else if (itemId == R.id.clear_list) {
            showClearDownloadHistoryPrompt();
            return true;
        } else if (itemId == R.id.start_downloads) {
            mBinder.getDownloadManager().startAllMissions();
            return true;
        } else if (itemId == R.id.pause_downloads) {
            mBinder.getDownloadManager().pauseAllMissions(false);
            mAdapter.refreshMissionItems();
            return super.onOptionsItemSelected(item);
        }
        return super.onOptionsItemSelected(item);
    }

    public void showClearDownloadHistoryPrompt() {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.clear_download_history)
                .setMessage(R.string.confirm_prompt)
                .setNegativeButton(R.string.clear_download_history, (dialog, which) ->
                        mAdapter.clearFinishedDownloads(false))
                .setNeutralButton(R.string.cancel, null)
                .setPositiveButton(R.string.delete_downloaded_files, (dialog, which) ->
                        showDeleteDownloadedFilesConfirmationPrompt())
                .show();
    }

    public void showDeleteDownloadedFilesConfirmationPrompt() {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.delete_downloaded_files_confirm)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ok, (dialog, which) ->
                        mAdapter.clearFinishedDownloads(true))
                .show();
    }

    private void updateList() {
        if (mLinear) {
            mList.setLayoutManager(mLinearManager);
        } else {
            mList.setLayoutManager(mGridManager);
        }

        mList.setAdapter(null);
        mAdapter.notifyDataSetChanged();

        mAdapter.setLinear(mLinear);
        mList.setAdapter(mAdapter);

        if (mSwitch != null) {
            mSwitch.setIcon(mLinear ? R.drawable.ic_apps : R.drawable.ic_list);
            mSwitch.setTitle(mLinear ? R.string.grid : R.string.list);
            mPrefs.edit().putBoolean("linear", mLinear).apply();
        }
    }

    private void setAdapterButtons() {
        if (mClear == null || mStart == null || mPause == null) return;
        mAdapter.setClearButton(mClear);
        mAdapter.setMasterButtons(mStart, mPause);
    }

    private void applyDownloadFilter(final int checkedId) {
        if (mAdapter != null) {
            mAdapter.setVideosOnly(checkedId == R.id.download_filter_videos);
        }
    }

    private void recoverMission(@NonNull DownloadMission mission) {
        unsafeMissionTarget = mission;

        final Uri initialPath;
        if (NewPipeSettings.useStorageAccessFramework(mContext)) {
            initialPath = null;
        } else {
            final File initialSavePath;
            if (DownloadManager.TAG_AUDIO.equals(mission.storage.getType())) {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MUSIC);
            } else {
                initialSavePath = NewPipeSettings.getDir(Environment.DIRECTORY_MOVIES);
            }
            initialPath = Uri.parse(initialSavePath.getAbsolutePath());
        }

        NoFileManagerSafeGuard.launchSafe(
                requestDownloadSaveAsLauncher,
                StoredFileHelper.getNewPicker(mContext, mission.storage.getName(),
                        mission.storage.getType(), initialPath),
                TAG,
                mContext
        );
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mAdapter != null) {
            mAdapter.onResume();

            if (mForceUpdate) {
                mForceUpdate = false;
                mAdapter.forceUpdate();
            }

            mBinder.addMissionEventListener(mAdapter);
            mAdapter.checkMasterButtonsVisibility();
        }
        if (mBinder != null) mBinder.enableNotifications(false);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (mAdapter != null) {
            mForceUpdate = true;
            mBinder.removeMissionEventListener(mAdapter);
            mAdapter.onPaused();
        }

        if (mBinder != null) mBinder.enableNotifications(true);
    }

    private void requestDownloadSaveAsResult(final ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) return;
        if (unsafeMissionTarget == null || result.getData() == null) return;

        try {
            Uri fileUri = result.getData().getData();
            if (fileUri.getAuthority() != null && FilePickerActivityHelper.isOwnFileUri(mContext, fileUri)) {
                fileUri = Uri.fromFile(Utils.getFileForUri(fileUri));
            }

            String tag = unsafeMissionTarget.storage.getTag();
            unsafeMissionTarget.storage = new StoredFileHelper(mContext, null, fileUri, tag);
            mAdapter.recoverMission(unsafeMissionTarget);
        } catch (IOException e) {
            Toast.makeText(mContext, R.string.general_error, Toast.LENGTH_LONG).show();
        }
    }
}
