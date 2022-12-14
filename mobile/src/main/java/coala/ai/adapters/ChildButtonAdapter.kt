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


class ChildButtonAdapter(private val mContext: Context , private val membersList: List<ButtonsData>) :
    RecyclerView.Adapter<ChildButtonAdapter.DataViewHolder>() {

    init {
        setHasStableIds(true)
    }

    var clicked: Boolean = false
    var clickedPos : Int? = null
    var onClickListener: OnItemClickListener? = null

    interface OnItemClickListener {
        fun itemClicked(position: Int , clickVal : Boolean)
    }

    fun setOnItemClickListener(listener: OnItemClickListener) {
        onClickListener = listener
    }


    fun refreshList(i : Int){
        clicked = true
        clickedPos = i
    }


    inner class DataViewHolder(itemView: View , private val i: Int) : RecyclerView.ViewHolder(itemView) {
        fun bind(result: ButtonsData) {
            try{
                val title = result.title
                itemView.button_name.text = title.replace(Regex("""[()]"""), "")
            }catch(ex: Exception){
                Log.i("title parsing error",ex.message.toString())
            }

            if (clicked &&  clickedPos != absoluteAdapterPosition){
                itemView.button_name.setBackgroundColor(mContext.getColor(R.color.card_grey))

            }


            itemView.setOnClickListener{
                onClickListener?.itemClicked(i, clicked)
            }


        }

    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = DataViewHolder(
            LayoutInflater.from(parent.context).inflate(
                    R.layout.item_row_child, parent,
                    false
            ), viewType
    )

    override fun onBindViewHolder(holder: DataViewHolder, position: Int) {
        holder.bind(membersList[position])

    }

    override fun getItemCount(): Int = membersList.size


    override fun getItemViewType(position: Int): Int {
        return position
    }


}