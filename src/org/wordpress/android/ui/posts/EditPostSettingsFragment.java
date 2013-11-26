package org.wordpress.android.ui.posts;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.actionbarsherlock.app.SherlockFragment;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.commons.lang.StringEscapeUtils;
import org.json.JSONArray;
import org.wordpress.android.R;
import org.wordpress.android.models.Post;
import org.wordpress.android.ui.media.MediaUtils;
import org.wordpress.android.util.JSONUtil;
import org.wordpress.android.util.LocationHelper;
import org.xmlrpc.android.ApiHelper;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

/**
 * Created by dan on 11/20/13.
 */
public class EditPostSettingsFragment extends SherlockFragment implements View.OnClickListener {

    private static final int ACTIVITY_REQUEST_CODE_SELECT_CATEGORIES = 5;

    private static final String CATEGORY_PREFIX_TAG = "category-";

    private Post mPost;

    private NewEditPostActivity mActivity;


    private Spinner mStatusSpinner;
    private EditText mPasswordEditText, mTagsEditText, mExcerptEditText;
    private TextView mLocationText, mPubDateText;
    private ViewGroup mSectionCategories;

    private ArrayList<String> mCategories;

    private Location mCurrentLocation;
    private LocationHelper mLocationHelper;

    private int mYear, mMonth, mDay, mHour, mMinute;
    private long mCustomPubDate = 0;
    private boolean mIsCustomPubDate;

    private String[] mPostFormats;
    private String[] mPostFormatTitles;

    private static enum LocationStatus {NONE, FOUND, NOT_FOUND, SEARCHING}

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.pubDateButton) {
            DatePickerDialog d = new DatePickerDialog(getActivity(),
                    R.style.WordPress, mDateSetListener, mYear, mMonth, mDay);
            d.show();
        } else if (id == R.id.selectCategories) {
            Bundle bundle = new Bundle();
            bundle.putInt("id", mPost.getBlogID());
            if (mCategories.size() > 0) {
                bundle.putSerializable("categories", new HashSet<String>(mCategories));
            }
            Intent categoriesIntent = new Intent(getActivity(), SelectCategoriesActivity.class);
            categoriesIntent.putExtras(bundle);
            startActivityForResult(categoriesIntent, ACTIVITY_REQUEST_CODE_SELECT_CATEGORIES);
        } else if (id == R.id.categoryButton) {
            onCategoryButtonClick(v);
        } else if (id == R.id.viewMap) {
            Double latitude = 0.0;
            try {
                latitude = mCurrentLocation.getLatitude();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (latitude != 0.0) {
                String uri = "geo:" + latitude + "," + mCurrentLocation.getLongitude();
                startActivity(new Intent(android.content.Intent.ACTION_VIEW, Uri.parse(uri)));
            } else {
                Toast.makeText(getActivity(), getResources().getText(R.string.location_toast), Toast.LENGTH_SHORT).show();
            }
        } else if (id == R.id.updateLocation) {
            getLocation();
        } else if (id == R.id.removeLocation) {
            removeLocation();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Calendar c = Calendar.getInstance();
        mYear = c.get(Calendar.YEAR);
        mMonth = c.get(Calendar.MONTH);
        mDay = c.get(Calendar.DAY_OF_MONTH);
        mHour = c.get(Calendar.HOUR_OF_DAY);
        mMinute = c.get(Calendar.MINUTE);
        mCategories = new ArrayList<String>();

        ViewGroup rootView = (ViewGroup) inflater
                .inflate(R.layout.fragment_edit_post_settings, container, false);

        if (rootView == null)
            return null;

        mActivity = (NewEditPostActivity) getActivity();

        mExcerptEditText = (EditText) rootView.findViewById(R.id.postExcerpt);
        mPasswordEditText = (EditText) rootView.findViewById(R.id.post_password);
        mLocationText = (TextView) rootView.findViewById(R.id.locationText);
        Button mPubDateButton = (Button) rootView.findViewById(R.id.pubDateButton);
        mPubDateText = (TextView) rootView.findViewById(R.id.pubDate);
        mStatusSpinner = (Spinner) rootView.findViewById(R.id.status);
        mTagsEditText = (EditText) rootView.findViewById(R.id.tags);
        mSectionCategories = ((ViewGroup) rootView.findViewById(R.id.sectionCategories));

        mPubDateButton.setOnClickListener(this);

        initLocation();

        // Set header labels to upper case
        ((TextView) rootView.findViewById(R.id.categoryLabel)).setText(getResources().getString(R.string.categories).toUpperCase());
        ((TextView) rootView.findViewById(R.id.statusLabel)).setText(getResources().getString(R.string.status).toUpperCase());
        ((TextView) rootView.findViewById(R.id.postFormatLabel)).setText(getResources().getString(R.string.post_format).toUpperCase());
        ((TextView) rootView.findViewById(R.id.pubDateLabel)).setText(getResources().getString(R.string.publish_date).toUpperCase());

        if (mActivity.getPost().isPage()) { // remove post specific views
            mExcerptEditText.setVisibility(View.GONE);
            (rootView.findViewById(R.id.sectionTags)).setVisibility(View.GONE);
            (rootView.findViewById(R.id.sectionCategories)).setVisibility(View.GONE);
            (rootView.findViewById(R.id.sectionLocation)).setVisibility(View.GONE);
            (rootView.findViewById(R.id.postFormatLabel)).setVisibility(View.GONE);
            (rootView.findViewById(R.id.postFormat)).setVisibility(View.GONE);
        } else {
            if (mActivity.getPost().getBlog().getPostFormats().equals("")) {
                List<Object> args = new Vector<Object>();
                args.add(mActivity.getPost().getBlog());
                args.add(mActivity);
                new ApiHelper.getPostFormatsTask().execute(args);
                mPostFormatTitles = getResources().getStringArray(R.array.post_formats_array);
                mPostFormats = new String[] {"aside", "audio", "chat", "gallery", "image", "link", "quote", "standard", "status", "video"};
            } else {
                try {
                    Gson gson = new Gson();
                    Type type = new TypeToken<Map<String, String>>() {
                    }.getType();
                    Map<String, String> jsonPostFormats = gson.fromJson(mActivity.getPost().getBlog().getPostFormats(), type);
                    mPostFormats = new String[jsonPostFormats.size()];
                    mPostFormatTitles = new String[jsonPostFormats.size()];
                    int i = 0;
                    for (Map.Entry<String, String> entry : jsonPostFormats.entrySet()) {
                        String key = entry.getKey();
                        String val = entry.getValue();
                        mPostFormats[i] = key;
                        mPostFormatTitles[i] = StringEscapeUtils.unescapeHtml(val);
                        i++;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            Spinner pfSpinner = (Spinner) rootView.findViewById(R.id.postFormat);
            ArrayAdapter<String> pfAdapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, mPostFormatTitles);
            pfAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            pfSpinner.setAdapter(pfAdapter);
            String activePostFormat = "standard";


            if (!mActivity.getPost().getWP_post_format().equals(""))
                activePostFormat = mActivity.getPost().getWP_post_format();
            for (int i = 0; i < mPostFormats.length; i++) {
                if (mPostFormats[i].equals(activePostFormat))
                    pfSpinner.setSelection(i);
            }
        }

        mPost = mActivity.getPost();
        if (mPost != null) {
            mExcerptEditText.setText(mPost.getMt_excerpt());

            String[] items = new String[]{getResources().getString(R.string.publish_post), getResources().getString(R.string.draft),
                    getResources().getString(R.string.pending_review), getResources().getString(R.string.post_private)};

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, items);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            mStatusSpinner.setAdapter(adapter);

            if (mPost.isUploaded()) {
                items = new String[]{
                        getResources().getString(R.string.publish_post),
                        getResources().getString(R.string.draft),
                        getResources().getString(R.string.pending_review),
                        getResources().getString(R.string.post_private)
                };
                adapter = new ArrayAdapter<String>(getActivity(), android.R.layout.simple_spinner_item, items);
                mStatusSpinner.setAdapter(adapter);
            }

            long pubDate = mPost.getDate_created_gmt();
            if (pubDate != 0) {
                try {
                    int flags = 0;
                    flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
                    flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
                    flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;
                    flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
                    String formattedDate = DateUtils.formatDateTime(getActivity(), pubDate,
                            flags);
                    mPubDateText.setText(formattedDate);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            if (mPost.getWP_password() != null)
                mPasswordEditText.setText(mPost.getWP_password());

            if (mPost.getPost_status() != null) {
                String status = mPost.getPost_status();

                if (status.equals("publish")) {
                    mStatusSpinner.setSelection(0, true);
                } else if (status.equals("draft")) {
                    mStatusSpinner.setSelection(1, true);
                } else if (status.equals("pending")) {
                    mStatusSpinner.setSelection(2, true);
                } else if (status.equals("private")) {
                    mStatusSpinner.setSelection(3, true);
                } else if (status.equals("localdraft")) {
                    mStatusSpinner.setSelection(0, true);
                }
            }

            if (!mPost.isPage()) {
                if (mPost.getJSONCategories() != null) {
                    mCategories = JSONUtil.fromJSONArrayToStringList(mPost.getJSONCategories());
                }

                Double latitude = mPost.getLatitude();
                Double longitude = mPost.getLongitude();

                // if this post has location attached to it, look up the location address
                if (latitude != 0.0) {
                    setLocationStatus(LocationStatus.SEARCHING);
                    new GetAddressTask().execute(latitude, longitude);
                }
            }
            String tags = mPost.getMt_keywords();
            if (!tags.equals("")) {
                mTagsEditText.setText(tags);
            }

            populateSelectedCategories();
        }

        return rootView;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (data != null || ((requestCode == MediaUtils.RequestCode.ACTIVITY_REQUEST_CODE_TAKE_PHOTO || requestCode == MediaUtils.RequestCode.ACTIVITY_REQUEST_CODE_TAKE_VIDEO))) {
            Bundle extras;

            switch (requestCode) {
                case ACTIVITY_REQUEST_CODE_SELECT_CATEGORIES:
                    extras = data.getExtras();
                    if (extras != null && extras.containsKey("selectedCategories")) {
                        mCategories = (ArrayList<String>) extras.getSerializable("selectedCategories");
                        populateSelectedCategories();
                    }
                    break;
            }
        }
    }

    public void savePostSettings(boolean isAutoSave) {
        if (mPost == null)
            return;

        String password = (mPasswordEditText.getText() != null) ? mPasswordEditText.getText().toString() : "";
        String pubDate = (mPubDateText.getText() != null) ? mPubDateText.getText().toString() : "";
        String excerpt = (mExcerptEditText.getText() != null) ? mExcerptEditText.getText().toString() : "";

        long pubDateTimestamp = 0;
        if (!pubDate.equals(getResources().getText(R.string.immediately))) {
            if (mIsCustomPubDate)
                pubDateTimestamp = mCustomPubDate;
            else if (mPost.getDate_created_gmt() > 0)
                pubDateTimestamp = mPost.getDate_created_gmt();
        }

        String tags = "", postFormat = "";
        if (!mPost.isPage()) {
            tags = (mTagsEditText.getText() != null) ? mTagsEditText.getText().toString() : "";
            // post format
            Spinner postFormatSpinner = (Spinner) getActivity().findViewById(R.id.postFormat);
            postFormat = mPostFormats[postFormatSpinner.getSelectedItemPosition()];
        }

        int selectedStatus = mStatusSpinner.getSelectedItemPosition();
        String status = "";

        switch (selectedStatus) {
            case 0:
                status = "publish";
                break;
            case 1:
                status = "draft";
                break;
            case 2:
                status = "pending";
                break;
            case 3:
                status = "private";
                break;
        }

        Double latitude = 0.0;
        Double longitude = 0.0;
        if (mPost.getBlog().isLocation()) {
            try {
                latitude = mCurrentLocation.getLatitude();
                longitude = mCurrentLocation.getLongitude();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (mCurrentLocation == null) {
            latitude = mPost.getLatitude();
            longitude = mPost.getLongitude();
        }

        mPost.setMt_excerpt(excerpt);
        mPost.setDate_created_gmt(pubDateTimestamp);
        mPost.setJSONCategories(new JSONArray(mCategories));
        mPost.setMt_keywords(tags);
        mPost.setPost_status(status);
        mPost.setWP_password(password);
        mPost.setLatitude(latitude);
        mPost.setLongitude(longitude);
        mPost.setWP_post_form(postFormat);
        mPost.update();
    }

    /**
     * Location methods
     */

    /*
     * retrieves and displays the friendly address for a lat/long location
     */
    private class GetAddressTask extends AsyncTask<Double, Void, String> {
        double latitude;
        double longitude;

        @Override
        protected String doInBackground(Double... args) {
            // args will be the latitude, longitude to look up
            latitude = args[0];
            longitude = args[1];

            // first make sure a Geocoder service exists on this device (requires API 9)
            if (!Geocoder.isPresent())
                return null;

            Geocoder gcd = new Geocoder(getActivity(), Locale.getDefault());
            List<Address> addresses;
            try {
                addresses = gcd.getFromLocation(latitude, longitude, 1);

                // addresses may be null or empty if network isn't connected
                if (addresses == null || addresses.size() == 0)
                    return null;

                String locality = "", adminArea = "", country = "";
                if (addresses.get(0).getLocality() != null)
                    locality = addresses.get(0).getLocality();
                if (addresses.get(0).getAdminArea() != null)
                    adminArea = addresses.get(0).getAdminArea();
                if (addresses.get(0).getCountryName() != null)
                    country = addresses.get(0).getCountryName();

                return ((locality.equals("")) ? locality : locality + ", ")
                        + ((adminArea.equals("")) ? adminArea : adminArea + " ") + country;
            } catch (IOException e) {
                // may get "Unable to parse response from server" IOException here if Geocoder
                // service is hit too frequently
                e.printStackTrace();
                return null;
            }
        }

        protected void onPostExecute(String result) {
            setLocationStatus(LocationStatus.FOUND);
            if (result == null || result.isEmpty()) {
                // show lat/long when Geocoder fails (ugly, but better than not showing anything
                // or showing an error since the location has been assigned to the post already)
                mLocationText.setText(Double.toString(latitude) + ", " + Double.toString(longitude));
            } else {
                mLocationText.setText(result);
            }
        }
    }

    private LocationHelper.LocationResult locationResult = new LocationHelper.LocationResult() {
        @Override
        public void gotLocation(final Location location) {
            // note that location will be null when requesting location fails
            getActivity().runOnUiThread(new Runnable() {
                public void run() {
                    setLocation(location);
                }
            });
        }
    };

    /*
     * called when activity is created to initialize the location provider, show views related
     * to location if enabled for this blog, and retrieve the current location if necessary
     */
    private void initLocation() {
        boolean hasLocationProvider = false;
        LocationManager locationManager = (LocationManager) getActivity().getSystemService(Activity.LOCATION_SERVICE);
        List<String> providers = locationManager.getProviders(true);
        if (providers != null) {
            for (String providerName : providers) {
                if (providerName.equals(LocationManager.GPS_PROVIDER) || providerName.equals(LocationManager.NETWORK_PROVIDER)) {
                    hasLocationProvider = true;
                }
            }
        }

        // show the location views if a provider was found and this is a post on a blog that has location enabled
        if (hasLocationProvider && mPost.getBlog().isLocation() && !mPost.isPage()) {
            getActivity().findViewById(R.id.sectionLocation).setVisibility(View.VISIBLE);
            Button viewMap = (Button) getActivity().findViewById(R.id.viewMap);
            Button updateLocation = (Button) getActivity().findViewById(R.id.updateLocation);
            Button removeLocation = (Button) getActivity().findViewById(R.id.removeLocation);
            updateLocation.setOnClickListener(this);
            removeLocation.setOnClickListener(this);
            viewMap.setOnClickListener(this);

            // if this is a new post, get the user's current location
            if (mPost.isNew()) {
                // TODO this is broken now
                getLocation();
            }
        }
    }

    /*
     * get the current location
     */
    private void getLocation() {
        if (mLocationHelper == null)
            mLocationHelper = new LocationHelper();
        boolean canGetLocation = mLocationHelper.getLocation(getActivity(), locationResult);
        if (canGetLocation) {
            setLocationStatus(LocationStatus.SEARCHING);
            mLocationText.setText(getString(R.string.loading));
        } else {
            setLocation(null);
        }
    }

    /*
     * called when location is retrieved/updated for this post - looks up the address to
     * display for the lat/long
     */
    private void setLocation(Location location) {
        if (location != null) {
            mCurrentLocation = location;
            new GetAddressTask().execute(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude());
        } else {
            mLocationText.setText(getString(R.string.location_not_found));
            setLocationStatus(LocationStatus.NOT_FOUND);
        }
    }

    private void removeLocation() {
        if (mCurrentLocation != null) {
            mCurrentLocation.setLatitude(0.0);
            mCurrentLocation.setLongitude(0.0);
        }
        if (mPost != null) {
            mPost.setLatitude(0.0);
            mPost.setLongitude(0.0);
        }
        mLocationText.setText("");
        setLocationStatus(LocationStatus.NONE);
    }

    /*
     * changes the left drawable on the location text to match the passed status
     */
    private void setLocationStatus(LocationStatus status) {
        // animate location text when searching
        if (status == LocationStatus.SEARCHING) {
            Animation aniBlink = AnimationUtils.loadAnimation(getActivity(), R.anim.blink);
            if (aniBlink != null)
                mLocationText.startAnimation(aniBlink);
        } else {
            mLocationText.clearAnimation();
        }

        final int drawableId;
        switch (status) {
            case FOUND:
                drawableId = R.drawable.ic_action_location_found;
                break;
            case NOT_FOUND:
                drawableId = R.drawable.ic_action_location_off;
                break;
            case SEARCHING:
                drawableId = R.drawable.ic_action_location_searching;
                break;
            case NONE:
                drawableId = 0;
                break;
            default:
                return;
        }

        mLocationText.setCompoundDrawablesWithIntrinsicBounds(drawableId, 0, 0, 0);
    }

    /**
     * Categories
     */

    private void onCategoryButtonClick(View v) {
        // Get category name by removing prefix from the tag
        boolean listChanged = false;
        String categoryName = (String) v.getTag();
        categoryName = categoryName.replaceFirst(CATEGORY_PREFIX_TAG, "");

        // Remove clicked category from list
        for (int i = 0; i < mCategories.size(); i++) {
            if (mCategories.get(i).equals(categoryName)) {
                mCategories.remove(i);
                listChanged = true;
                break;
            }
        }

        // Recreate category views
        if (listChanged) {
            populateSelectedCategories();
        }
    }

    private void populateSelectedCategories() {
        // Remove previous category buttons if any + select category button
        List<View> viewsToRemove = new ArrayList<View>();
        for (int i = 0; i < mSectionCategories.getChildCount(); i++) {
            View v = mSectionCategories.getChildAt(i);
            if (v == null)
                return;
            Object tag = v.getTag();
            if (tag != null && tag.getClass() == String.class &&
                    (((String) tag).startsWith(CATEGORY_PREFIX_TAG) || tag.equals("select-category"))) {
                viewsToRemove.add(v);
            }
        }
        for (View viewToRemove : viewsToRemove) {
            mSectionCategories.removeView(viewToRemove);
        }
        viewsToRemove.clear();

        // New category buttons
        LayoutInflater layoutInflater = getActivity().getLayoutInflater();
        for (String categoryName : mCategories) {
            Button buttonCategory = (Button) layoutInflater.inflate(R.layout.category_button, null);
            if (categoryName != null && buttonCategory != null) {
                buttonCategory.setText(Html.fromHtml(categoryName));
                buttonCategory.setTag(CATEGORY_PREFIX_TAG + categoryName);
                buttonCategory.setOnClickListener(this);
                mSectionCategories.addView(buttonCategory);
            }
        }

        // Add select category button
        Button selectCategory = (Button) layoutInflater.inflate(R.layout.category_select_button, null);
        if (selectCategory != null) {
            selectCategory.setOnClickListener(this);
            mSectionCategories.addView(selectCategory);
        }
    }

    /**
     * Date/Time
     */

    private DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener() {
        public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
            mYear = year;
            mMonth = monthOfYear;
            mDay = dayOfMonth;
            TimePickerDialog tpd = new TimePickerDialog(getActivity(), mTimeSetListener, mHour, mMinute, false);
            tpd.setTitle("");
            tpd.show();
        }
    };

    private TimePickerDialog.OnTimeSetListener mTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

        public void onTimeSet(TimePicker view, int hour, int minute) {
            mHour = hour;
            mMinute = minute;

            Date d = new Date(mYear - 1900, mMonth, mDay, mHour, mMinute);
            long timestamp = d.getTime();

            try {
                int flags = 0;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_DATE;
                flags |= android.text.format.DateUtils.FORMAT_ABBREV_MONTH;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_YEAR;
                flags |= android.text.format.DateUtils.FORMAT_SHOW_TIME;
                String formattedDate = DateUtils.formatDateTime(getActivity(), timestamp, flags);
                mCustomPubDate = timestamp;
                mPubDateText.setText(formattedDate);
                mIsCustomPubDate = true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };
}