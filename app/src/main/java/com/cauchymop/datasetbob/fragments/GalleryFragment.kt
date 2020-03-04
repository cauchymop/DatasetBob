/*
 * Copyright 2019 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cauchymop.datasetbob.fragments

import android.media.MediaScannerConnection
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.gridlayout.widget.GridLayout
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.viewpager.widget.ViewPager
import com.cauchymop.datasetbob.DatasetBobViewModel
import com.cauchymop.datasetbob.R
import com.cauchymop.datasetbob.utils.showImmersive
import java.io.File
import kotlin.math.ceil


val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

  private lateinit var mediaViewPager: ViewPager

  /** AndroidX navigation arguments */
  private val args: GalleryFragmentArgs by navArgs()

  private lateinit var mediaList: MutableList<File>
  private lateinit var viewModel: DatasetBobViewModel

  /** Adapter class used to present a fragment containing one photo or video as a page */
  inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
    override fun getCount(): Int = mediaList.size
    override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
    override fun getItemPosition(obj: Any): Int = POSITION_NONE
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
    retainInstance = true

    // Get root directory of media from navigation arguments
    val rootDirectory = File(args.rootDirectory)

    // Walk through all files in the root directory
    // We reverse the order of the list to present the last photos first
    mediaList = rootDirectory.listFiles { file ->
      EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
    }?.sortedDescending()?.toMutableList() ?: mutableListOf()
  }

  override fun onActivityCreated(savedInstanceState: Bundle?) {
    super.onActivityCreated(savedInstanceState)
    viewModel = ViewModelProviders.of(requireActivity()).get(DatasetBobViewModel::class.java)
    viewModel.requestSignIn(requireActivity())

    viewModel.datasets.observe(viewLifecycleOwner, Observer {
      if (it.isNotEmpty()) {
        viewModel.selectDataset(it[0])
      }
    })

    viewModel.currentDataset.observe(viewLifecycleOwner, Observer {
      view?.findViewById<TextView>(R.id.current_dataset_name)?.text = it.name
    })

    viewModel.categories.observe(viewLifecycleOwner, Observer { labels ->
      view?.findViewById<GridLayout>(R.id.buttonGrid)?.run {
        removeAllViews()
        rowCount = ceil(labels.size / 2f).toInt();
        labels.forEachIndexed { index, label ->
          val button = Button(context).apply {
            text = label
            setOnClickListener {
              classify(label)
            }
          }
          addView(button, index)
        }
      }
    })
  }

  override fun onCreateView(
      inflater: LayoutInflater,
      container: ViewGroup?,
      savedInstanceState: Bundle?
  ): View? = inflater.inflate(R.layout.fragment_gallery, container, false)

  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)

    // Populate the ViewPager and implement a cache of two media items
    mediaViewPager = view.findViewById<ViewPager>(R.id.photo_view_pager).apply {
      offscreenPageLimit = 2
      adapter = MediaPagerAdapter(childFragmentManager)
    }

    // Handle back button press
    view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
      Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
    }

    // Handle delete button press
    view.findViewById<ImageButton>(R.id.delete_button).setOnClickListener {
      onDelete(view)
    }

    view.findViewById<Button>(R.id.choose_dataset).setOnClickListener {
      onChooseDataset()
    }
  }

  private fun onChooseDataset() {

  }

  private fun GalleryFragment.onDelete(view: View) {
    AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
      .setTitle(getString(R.string.delete_title))
      .setMessage(getString(R.string.delete_dialog))
      .setIcon(android.R.drawable.ic_dialog_alert)
      .setPositiveButton(android.R.string.yes) { _, _ ->
        mediaList.getOrNull(mediaViewPager.currentItem)?.let { mediaFile ->

          // Delete current photo
          mediaFile.delete()

          // Send relevant broadcast to notify other apps of deletion
          MediaScannerConnection.scanFile(
            view.context, arrayOf(mediaFile.absolutePath), null, null
          )

          // Notify our view pager
          mediaList.removeAt(mediaViewPager.currentItem)
          mediaViewPager.adapter?.notifyDataSetChanged()

          // If all photos have been deleted, return to camera
          if (mediaList.isEmpty()) {
            fragmentManager?.popBackStack()
          }
        }
      }

      .setNegativeButton(android.R.string.no, null)
      .create().showImmersive()
  }

  private fun classify(label: String) {
    mediaList.getOrNull(mediaViewPager.currentItem)?.let { mediaFile ->
      val renamed = classify(mediaFile, label)
      mediaFile.renameTo(renamed)
      mediaList.set(mediaViewPager.currentItem, renamed)
      mediaViewPager.adapter?.notifyDataSetChanged()
    }
  }

  private fun classify(mediaFile: File, label: String): File {
    return File(mediaFile.absolutePath.replace("""(_[a-z]*)?.jpg""".toRegex(), "_$label.jpg"))
  }
}