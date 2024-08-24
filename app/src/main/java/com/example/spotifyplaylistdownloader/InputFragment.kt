package com.example.spotifyplaylistdownloader

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.text.Editable
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat.getSystemService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.get


// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [InputFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class InputFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var clipboardManager: ClipboardManager
    private lateinit var sharedViewModel: SharedViewModel

    private lateinit var mainActivity: MainActivity

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is MainActivity) {
            mainActivity = context
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_input, container, false)

        //widgets
        val continueButton = view.findViewById<Button>(R.id.continueButton)
        val pasteButton = view.findViewById<ImageButton>(R.id.pasteClipboardButton)
        val editText = view.findViewById<EditText>(R.id.editText)
        val loadingBar = view.findViewById<ProgressBar>(R.id.loading_wheel)


        //initilaize the permission launcher
        permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions.entries.all { it.value }
            if (!granted) {
                Toast.makeText(context, "Notifications are disabled - allow notification in settings", Toast.LENGTH_SHORT).show()
            }
        }

        //get permissions
        updateOrRequestPermissions()

        clipboardManager = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        //paste from clipboard button
        pasteButton.setOnClickListener {
            val clipData: ClipData? = clipboardManager.primaryClip
            //check if it is not empty
            if (clipData != null && clipData.itemCount > 0) {
                val item: ClipData.Item = clipData.getItemAt(0)
                val clipboardText: CharSequence? = item.text
                editText.setText(clipboardText)
            }
            else {
                Toast.makeText(requireContext(), "No copied data", Toast.LENGTH_SHORT).show()
            }

        }

        loadingBar.visibility = View.INVISIBLE

        //button press action
        continueButton.setOnClickListener {

            //check if the user is connected
            val connected = checkInternetConnectivity(requireContext())
            if (!connected) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Not connected")
                    .setMessage("Please connect to the internet to proceed")
                    .setPositiveButton("Ok") {_, _ -> }
                    .show()
                return@setOnClickListener
            }
            //show loading bar
            loadingBar.visibility = View.VISIBLE
            pasteButton.visibility = View.INVISIBLE

            val spotifyLink = editText.text.toString()

            //check if the input is valid
            val isValid = myFunNames.call(spotifyLink, "validate")?.toString()
            Log.println(Log.DEBUG, "test", isValid.toString())

            if (isValid == "False") {

                //show toast if it is wrong
                Toast.makeText(context, "Enter a valid link", Toast.LENGTH_SHORT).show()

                //vibrate if it is wrong
                val vibrator = requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(400, 200 , 400)
                if (vibrator.hasVibrator()) {vibrator.vibrate(200)}
                loadingBar.visibility = View.INVISIBLE
                pasteButton.visibility = View.VISIBLE
            }
            else {


                val bundle = Bundle().apply {
                    putString("link", spotifyLink)
                }

                val playlistFragment = PlaylistFragment().apply {
                    arguments = bundle
                }

                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, playlistFragment)
                    .addToBackStack("InputFragment")
                    .commit()
            }
        }

        return view
    }

    private fun updateOrRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

//        val hasReadPermission = ContextCompat.checkSelfPermission(
//            requireContext(),
//            Manifest.permission.READ_EXTERNAL_STORAGE
//        ) == PackageManager.PERMISSION_GRANTED
//        val hasWritePermission = ContextCompat.checkSelfPermission(
//            requireContext(),
//            Manifest.permission.WRITE_EXTERNAL_STORAGE
//        ) == PackageManager.PERMISSION_GRANTED

        //check if notification is granted (only sdk 33 and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
//            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
        }

//        val readPermissionGranted = hasReadPermission
//        val writePermissionGranted = hasWritePermission

//        if (!readPermissionGranted) {
//            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
//        }
//        if (!writePermissionGranted) {
//            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
//        }
//        if (permissionsToRequest.isNotEmpty()) {
//            permissionLauncher.launch(permissionsToRequest.toTypedArray())
//        }
    }





    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment InputFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            InputFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}