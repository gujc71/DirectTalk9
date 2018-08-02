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

import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;

import gujc.directtalk9.R;
import gujc.directtalk9.model.ChatModel;
import gujc.directtalk9.model.ChatRoomModel;
import gujc.directtalk9.model.UserModel;

public class ViewPagerActivity extends AppCompatActivity {

	private static String roomID;
	private static String realname;
	private static ViewPager viewPager;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_pager);

		roomID = getIntent().getStringExtra("roomID");
		realname = getIntent().getStringExtra("realname");

		viewPager = findViewById(R.id.view_pager);
		viewPager.setAdapter(new SamplePagerAdapter());
	}

	static class SamplePagerAdapter extends PagerAdapter {
		private StorageReference storageReference;
		private ArrayList<String> imgList = new ArrayList<>();
		private int inx = -1;

		public SamplePagerAdapter() {
			storageReference  = FirebaseStorage.getInstance().getReference();

			FirebaseDatabase.getInstance().getReference().child("rooms").child(roomID).child("messages").addValueEventListener(new ValueEventListener(){
				@Override
				public void onDataChange(DataSnapshot dataSnapshot) {
					for (DataSnapshot item: dataSnapshot.getChildren()) {
						final ChatModel.Message message = item.getValue(ChatModel.Message.class);
						if ("1".equals(message.msgtype)) {
							imgList.add(message.msg);
							if (realname.equals(message.msg)) {inx = imgList.size()-1; }
						}
					}
					notifyDataSetChanged();
					if (inx>-1) {
						viewPager.setCurrentItem(inx);
					}
				}

				@Override
				public void onCancelled(DatabaseError databaseError) {

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
			Glide.with(container.getContext())
					.load(storageReference.child("filesmall/"+imgList.get(position)))
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
