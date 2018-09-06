/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package gujc.directtalk9.photoview;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.Button;

import com.bumptech.glide.Glide;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.util.ArrayList;

import gujc.directtalk9.R;
import gujc.directtalk9.common.Util9;
import gujc.directtalk9.model.Message;

public class ViewPagerActivity extends AppCompatActivity {

	private static String roomID;
	private static String realname;
	private static ViewPager viewPager;
	private static ArrayList<Message> imgList = new ArrayList<>();
    private String rootPath = Util9.getRootPath()+"/DirectTalk9/";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pager);
		Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
		setSupportActionBar(toolbar);

		roomID = getIntent().getStringExtra("roomID");
		realname = getIntent().getStringExtra("realname");

		viewPager = findViewById(R.id.view_pager);
		viewPager.setAdapter(new SamplePagerAdapter());

        findViewById(R.id.downloadBtn).setOnClickListener(downloadBtnClickListener);
		//findViewById(R.id.rotateBtn).setOnClickListener(rotateBtnClickListener);

        ActionBar actionBar = getSupportActionBar();
        //actionBar.setIcon(R.drawable.back);
        actionBar.setTitle("PhotoView");
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setHomeButtonEnabled(true);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
			case android.R.id.home:
				onBackPressed();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	Button.OnClickListener downloadBtnClickListener = new View.OnClickListener() {
		public void onClick(final View view) {
            if (!Util9.isPermissionGranted((Activity) view.getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                return ;
            }
			Message message = imgList.get(viewPager.getCurrentItem());
            /// showProgressDialog("Downloading File.");

			final File localFile = new File(rootPath, message.getFilename());

			// realname == message.msg
			FirebaseStorage.getInstance().getReference().child("files/"+message.getMsg()).getFile(localFile).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
				@Override
				public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
					// hideProgressDialog();
					Util9.showMessage(view.getContext(), "Downloaded file");
					Log.e("DirectTalk9 ","local file created " +localFile.toString());
				}
			}).addOnFailureListener(new OnFailureListener() {
				@Override
				public void onFailure(@NonNull Exception exception) {
					Log.e("DirectTalk9 ","local file not created  " +exception.toString());
				}
			});
		}
	};

    Button.OnClickListener rotateBtnClickListener = new View.OnClickListener() {
        public void onClick(View view) {
            View child = viewPager.getChildAt(viewPager.getCurrentItem());
            PhotoView photoView = child.findViewById(R.id.photoView);
            photoView.setRotation(photoView.getRotation()+90);
        }
    };

	static class SamplePagerAdapter extends PagerAdapter {
		private StorageReference storageReference;
		private int inx = -1;

		public SamplePagerAdapter() {
			storageReference  = FirebaseStorage.getInstance().getReference();

			FirebaseFirestore.getInstance().collection("rooms").document(roomID).collection("messages").whereEqualTo("msgtype", "1")
					.get()
					.addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
						@Override
						public void onComplete(@NonNull Task<QuerySnapshot> task) {
							if (!task.isSuccessful()) { return;}

							for (QueryDocumentSnapshot document : task.getResult()) {
								Message message = document.toObject(Message.class);
								imgList.add(message);
								if (realname.equals(message.getMsg())) {inx = imgList.size()-1; }
							}
							notifyDataSetChanged();
							if (inx>-1) {
								viewPager.setCurrentItem(inx);
							}
						}
					});
		}

		@Override
		public int getCount() {
			return imgList.size();
		}

		@Override
		public View instantiateItem(final ViewGroup container, final int position) {
			final PhotoView photoView = new PhotoView(container.getContext());
            photoView.setId(R.id.photoView);

			Glide.with(container.getContext())
					.load(storageReference.child("filesmall/"+imgList.get(position).getMsg()))
					.into(photoView);

			container.addView(photoView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

			return photoView;
		}

		@Override
		public void destroyItem(ViewGroup container, int position, Object object) {
			container.removeView((View) object);
		}

		@Override
		public boolean isViewFromObject(View view, Object object) {
			return view == object;
		}

	}
}
