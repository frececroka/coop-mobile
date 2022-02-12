package de.lorenzgorse.coopmobile.ui.correspondences

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.firebase.analytics.FirebaseAnalytics
import de.lorenzgorse.coopmobile.R
import de.lorenzgorse.coopmobile.client.Correspondence
import de.lorenzgorse.coopmobile.createAnalytics
import de.lorenzgorse.coopmobile.data.data
import de.lorenzgorse.coopmobile.setScreen
import de.lorenzgorse.coopmobile.ui.RemoteDataView
import kotlinx.android.synthetic.main.fragment_correspondences.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@FlowPreview
@ExperimentalCoroutinesApi
class CorrespondencesFragment : Fragment() {

    private val dateFormat = SimpleDateFormat("EEE, d. MMMM yyyy", Locale.getDefault())

    private lateinit var remoteDataView: RemoteDataView
    private lateinit var inflater: LayoutInflater
    private lateinit var analytics: FirebaseAnalytics
    private lateinit var viewModel: CorrespondencesData

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        analytics = createAnalytics(requireContext())
        inflater = requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        viewModel = ViewModelProvider(this).get(CorrespondencesData::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        remoteDataView = RemoteDataView.inflate(inflater, container, R.layout.fragment_correspondences)
        return remoteDataView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        remoteDataView.bindState(viewModel.state)
    }

    override fun onStart() {
        super.onStart()
        analytics.setScreen("Correspondences")
        lifecycleScope.launch {
            viewModel.state.data().filterNotNull().collect {
                onSuccess(it)
            }
        }
    }

    private fun onSuccess(data: List<Correspondence>) {
        correspondences.removeAllViews()
        for (correspondence in data) {
            val productItemView = inflater.inflate(
                R.layout.correspondence, correspondences, false)
            val txtDate = productItemView.findViewById<TextView>(R.id.txtDate)
            val txtSubject = productItemView.findViewById<TextView>(R.id.txtSubject)
            val txtMessage = productItemView.findViewById<TextView>(R.id.txtMessage)

            txtDate.text = dateFormat.format(correspondence.header.date)
            txtSubject.text = correspondence.header.subject
            txtMessage.text = correspondence.message
            correspondences.addView(productItemView)
        }
    }

}
