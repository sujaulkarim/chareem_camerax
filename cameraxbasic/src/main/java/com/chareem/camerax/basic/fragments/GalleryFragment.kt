package com.chareem.camerax.basic.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ShareCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import com.chareem.camerax.basic.R
import com.chareem.camerax.basic.databinding.FragmentGalleryBinding
import com.chareem.camerax.basic.utils.CameraHelper
import com.chareem.camerax.basic.utils.padWithDisplayCutout
import com.chareem.camerax.basic.utils.showImmersive
import java.io.File
import java.lang.Exception
import java.util.*

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** Android ViewBinding */
    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null

    private val fragmentGalleryBinding get() = _fragmentGalleryBinding!!

    private lateinit var mediaList: MutableList<File>

    private var rootDirectory = ""
    private var imgDirectory = ""

    companion object{
        val root_directory = "root_directory"
        val img_directory = "img_directory"
    }

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        rootDirectory = requireArguments().getString(root_directory) ?: ""
        imgDirectory = requireArguments().getString(img_directory) ?: ""

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Get root directory of media from navigation arguments
        val rootDirectory = File(rootDirectory)

        // Walk through all files in the root directory
        // We reverse the order of the list to present the last photos first
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.uppercase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (imgDirectory.isEmpty()){
            fragmentGalleryBinding.saveButton.isVisible = false
        }

        //Checking media files list
        if (mediaList.isEmpty()) {
            fragmentGalleryBinding.saveButton.isEnabled = false
            fragmentGalleryBinding.closeButton.isEnabled = false
            fragmentGalleryBinding.deleteButton.isEnabled = false
            fragmentGalleryBinding.shareButton.isEnabled = false
        }

        // Populate the ViewPager and implement a cache of two media items
        fragmentGalleryBinding.photoViewPager.apply {
            offscreenPageLimit = 2
            adapter = MediaPagerAdapter(childFragmentManager)
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            fragmentGalleryBinding.cutoutSafeArea.padWithDisplayCutout()
        }

        // Handle back button press
        fragmentGalleryBinding.backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }

        // Handle share button press
        fragmentGalleryBinding.shareButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)?.let { mediaFile ->

                try {
                    // Create a sharing intent
                    val intent = Intent().apply {
                        // Infer media type from file extension
                        val mediaType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(mediaFile.extension)
                        // Get URI from our FileProvider implementation
                        val uri = FileProvider.getUriForFile(
                            view.context, requireContext().packageName+ ".provider", mediaFile)
                        // Set the appropriate intent extra, type, action and flags
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = mediaType
                        action = Intent.ACTION_SEND
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }

                    // Launch the intent letting the user choose which app to share with
                    startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))

                } catch (e : Exception){
                    e.printStackTrace()
                    Toast.makeText(requireContext(), "Failed to share, please check your setting provider", Toast.LENGTH_LONG).show()
                }

                /*val uri = Uri.fromFile(mediaFile)
                val mediaType = MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(mediaFile.extension)
                ShareCompat.IntentBuilder.from(requireActivity())
                    .setStream(uri)
                .setType(mediaType)
                .setSubject("Chareem CameraX")
                .setChooserTitle("Choose One")
                //.setText(link)
                .startChooser()*/
            }
        }

        // Handle delete button press
        fragmentGalleryBinding.deleteButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)?.let { mediaFile ->

                AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                        .setTitle(getString(R.string.delete_title))
                        .setMessage(getString(R.string.delete_dialog))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { _, _ ->

                            // Delete current photo
                            mediaFile.delete()

                            // Send relevant broadcast to notify other apps of deletion
                            MediaScannerConnection.scanFile(
                                    view.context, arrayOf(mediaFile.absolutePath), null, null)

                            // Notify our view pager
                            mediaList.removeAt(fragmentGalleryBinding.photoViewPager.currentItem)
                            fragmentGalleryBinding.photoViewPager.adapter?.notifyDataSetChanged()

                            // If all photos have been deleted, return to camera
                            if (mediaList.isEmpty()) {
                                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
                            }

                        }

                        .setNegativeButton(android.R.string.no, null)
                        .create().showImmersive()
            }
        }

        fragmentGalleryBinding.closeButton.setOnClickListener {
            requireActivity().finish()
        }

        fragmentGalleryBinding.saveButton.setOnClickListener {
            val resultIntent = Intent()
            resultIntent.putExtra(
                CameraHelper.RESULT_PATH,
                imgDirectory
            )
            requireActivity().setResult(Activity.RESULT_OK, resultIntent)
            requireActivity().finish()
        }
    }

    override fun onDestroyView() {
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }
}
