package coala.ai.adapters


import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.recyclerview.widget.RecyclerView
import coala.ai.R
import coala.ai.models.ButtonsData
import coala.ai.models.TableData
import kotlinx.android.synthetic.main.table_item_layout.view.*


class TableAdapter(private val mContext: Context, private val tableData : ArrayList<TableData>) :
    RecyclerView.Adapter<TableAdapter.TableViewHolder>() {


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TableViewHolder {
        val v: View =
            LayoutInflater.from(mContext).inflate(R.layout.table_item_layout, parent, false)
        return TableViewHolder(v)
    }

    override fun onBindViewHolder(holder: TableViewHolder, position: Int) {
        holder.bind(tableData[position])
        if (position % 2 == 1) {
            holder.itemView.setBackgroundColor(mContext.resources.getColor(R.color.white))
        } else {
            holder?.itemView.setBackgroundColor(mContext.resources.getColor(R.color.yellow))
        }
    }

    override fun getItemCount(): Int {
        return tableData.size
    }


    inner class TableViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        val id = itemView.val_defect_id
        val component = itemView.val_component
        val defect = itemView.val_defect
        val severity = itemView.val_severity
        val occurrence = itemView.val_occurrence

        fun bind(result: TableData) {
            id.text = result.Defect_ID
            component.text = result.Involved_component
            defect.text = result.Defect_Characteristic
            severity.text = result.Defect_Severity
            occurrence.text = result.Occurrences
        }
    }
}