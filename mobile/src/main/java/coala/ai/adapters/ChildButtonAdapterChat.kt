package coala.ai.adapters

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coala.ai.R
import coala.ai.models.ButtonsData
import kotlinx.android.synthetic.main.item_row_child.view.*

class ChildButtonAdapterChat (private val mContext: Context) :
    RecyclerView.Adapter<ChildButtonAdapterChat.DataViewHolder>() {

    private var membersList: List<ButtonsData> = ArrayList()

    fun addData(memberData: List<ButtonsData>){
        this.membersList = memberData
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DataViewHolder(
        LayoutInflater.from(parent.context).inflate(
            R.layout.item_row_child, parent,
            false
        )
    )

    override fun onBindViewHolder(holder: ChildButtonAdapterChat.DataViewHolder, position: Int) {
        holder.bind(membersList[position])
    }

    override fun getItemCount(): Int = membersList.size


    inner class DataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        init {

            itemView.isClickable=false
            Log.i("clickable","false button adapter")

        }

        fun bind(result: ButtonsData) {
            try{
                val title = result.title
                itemView.button_name.text = title.replace(Regex("""[()]"""), "")

            }catch(ex: Exception){
                Log.i("title parsing error",ex.message.toString())
            }


        }

    }
}