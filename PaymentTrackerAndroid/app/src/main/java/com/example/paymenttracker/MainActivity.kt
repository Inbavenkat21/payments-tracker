package com.example.paymenttracker

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BG = Color.rgb(9, 13, 17)
private val PANEL = Color.rgb(21, 27, 33)
private val PANEL2 = Color.rgb(28, 37, 44)
private val TEXT = Color.rgb(244, 248, 247)
private val MUTED = Color.rgb(145, 162, 168)
private val LIME = Color.rgb(200, 251, 98)
private val RED = Color.rgb(255, 155, 150)

class TxDb(context: Activity) : SQLiteOpenHelper(context, "payments.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE transactions(
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sms_key TEXT UNIQUE,
            date_ms INTEGER NOT NULL,
            description TEXT NOT NULL,
            amount REAL NOT NULL,
            kind TEXT NOT NULL,
            source TEXT NOT NULL,
            raw TEXT NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
    fun add(key:String,date:Long,desc:String,amount:Double,kind:String,source:String,raw:String):Boolean {
        val v=ContentValues().apply { put("sms_key",key);put("date_ms",date);put("description",desc);put("amount",amount);put("kind",kind);put("source",source);put("raw",raw) }
        return writableDatabase.insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1L
    }
    fun manual(date:Long,desc:String,amount:Double,kind:String,source:String,category:String) {
        val v=ContentValues().apply { put("sms_key","manual:${System.currentTimeMillis()}:${desc.hashCode()}");put("date_ms",date);put("description",desc);put("amount",amount);put("kind",kind);put("source",source);put("raw","category=$category") }
        writableDatabase.insertWithOnConflict("transactions",null,v,SQLiteDatabase.CONFLICT_IGNORE)
    }
    fun totals():Pair<Double,Double> {
        val c=readableDatabase.rawQuery("SELECT COALESCE(SUM(CASE WHEN kind='INCOME' THEN amount ELSE 0 END),0),COALESCE(SUM(CASE WHEN kind='EXPENSE' THEN amount ELSE 0 END),0) FROM transactions",null)
        c.moveToFirst();val p=c.getDouble(0) to c.getDouble(1);c.close();return p
    }
    data class Row(val id:Long,val date:Long,val name:String,val amount:Double,val kind:String,val source:String,val raw:String)
    fun rows():List<Row>{
        val out=mutableListOf<Row>();val c=readableDatabase.rawQuery("SELECT id,date_ms,description,amount,kind,source,raw FROM transactions ORDER BY date_ms DESC LIMIT 500",null)
        while(c.moveToNext()) out.add(Row(c.getLong(0),c.getLong(1),c.getString(2),c.getDouble(3),c.getString(4),c.getString(5),c.getString(6)))
        c.close();return out
    }
    fun count():Int { val c=readableDatabase.rawQuery("SELECT COUNT(*) FROM transactions",null);c.moveToFirst();val n=c.getInt(0);c.close();return n }
    fun delete(id:Long){writableDatabase.delete("transactions","id=?", arrayOf(id.toString()))}
}

class MainActivity : Activity() {
    private lateinit var db:TxDb
    private lateinit var root:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private var current="Home"
    private var hideBalance=false
    private var selectedFilter="All"
    private var searchText=""
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=BG;window.navigationBarColor=BG;window.decorView.systemUiVisibility=0;db=TxDb(this);render()}
    private fun dp(n:Int)= (n*resources.displayMetrics.density).toInt()
    private fun text(value:String,size:Float,color:Int=TEXT,bold:Boolean=false):TextView=TextView(this).apply{ text=value;textSize=size;setTextColor(color);if(bold)setTypeface(typeface,Typeface.BOLD);setPadding(0,dp(2),0,dp(2)) }
    private fun label(value:String)=text(value,12f,MUTED)
    private fun btn(value:String,action:()->Unit,primary:Boolean=false):Button=Button(this).apply{
        text=value; textSize=13f; isAllCaps=false; setTextColor(if(primary) BG else TEXT)
        backgroundTintList=android.content.res.ColorStateList.valueOf(if(primary)LIME else PANEL2)
        setOnClickListener{action()}
    }
    private fun panel(pad:Int=16)=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(pad),dp(pad),dp(pad),dp(pad));background=android.graphics.drawable.GradientDrawable().apply{setColor(PANEL);cornerRadius=dp(20).toFloat();setStroke(dp(1),Color.rgb(42,53,60))}}
    private fun row():LinearLayout=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun space(height:Int){body.addView(View(this),LinearLayout.LayoutParams(1,dp(height)))}
    private fun addPanel(v:View){body.addView(v,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(12)})}
    private fun money(n:Double)="₹"+String.format(Locale("en","IN"),"%,.2f",n).replace(".00","")
    private fun render(){
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(BG);setPadding(dp(18),dp(16),dp(18),dp(8))}
        val header=row()
        val brand=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        brand.addView(text("◈  FLUX.",22f,LIME,true));brand.addView(text("YOUR MONEY, REIMAGINED",9f,MUTED,true))
        header.addView(brand,LinearLayout.LayoutParams(0,-2,1f))
        header.addView(btn(if(hideBalance)"Show balance" else "Hide balance",{hideBalance=!hideBalance;render()}))
        root.addView(header)
        root.addView(text("DEMO DATA · NOT CONNECTED TO A BANK",10f,MUTED,true).apply{setPadding(0,dp(10),0,dp(10))})
        val scroll=ScrollView(this).apply{clipToPadding=false}
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        scroll.addView(body)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        status=text("",12f,MUTED);root.addView(status)
        val nav=row()
        listOf("Home","Activity","Insights","Accounts","Settings").forEach { item ->
            nav.addView(btn(item,{current=item;render()},current==item),LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(2);marginEnd=dp(2)})
        }
        root.addView(nav)
        when(current){
            "Home"->home()
            "Activity"->activity()
            "Insights"->insights()
            "Accounts"->accounts()
            else->settings()
        }
        setContentView(root)
    }
    private fun home(){
        space(8);body.addView(text("Good to see you.",14f,MUTED));body.addView(text("Financial overview",26f,TEXT,true));space(14)
        val (inc,out)=db.totals()
        val card=panel(20).apply{
            background=android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(26,40,33),Color.rgb(17,28,26))).apply{cornerRadius=dp(22).toFloat();setStroke(dp(1),Color.rgb(56,80,65))}
            addView(text("NET RECORDED CASH FLOW",11f,Color.rgb(174,196,180),true))
            addView(text(if(hideBalance)"₹ ••••••" else money(inc-out),31f,TEXT,true))
            addView(text("Illustrative total from saved records",11f,Color.rgb(174,196,180)))
            val split=row().apply{setPadding(0,dp(14),0,0)}
            val left=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(text("↙  MONEY IN",11f,LIME,true));addView(text(if(hideBalance)"••••" else money(inc),18f,TEXT,true))}
            val right=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;addView(text("↗  MONEY OUT",11f,RED,true));addView(text(if(hideBalance)"••••" else money(out),18f,TEXT,true))}
            split.addView(left,LinearLayout.LayoutParams(0,-2,1f));split.addView(right,LinearLayout.LayoutParams(0,-2,1f));addView(split)
        }
        addPanel(card)
        val quick=row()
        quick.addView(btn("+ Add transaction",{showAdd()},true),LinearLayout.LayoutParams(0,dp(48),1f).apply{marginEnd=dp(6)})
        quick.addView(btn("Import SMS",{importSms()},false),LinearLayout.LayoutParams(0,dp(48),1f).apply{marginStart=dp(6)})
        addPanel(quick)
        val insights=panel()
        insights.addView(text("SPENDING PULSE",11f,MUTED,true));insights.addView(text(if(hideBalance)"••••••" else money(out),24f,TEXT,true));insights.addView(text("Across recorded expenses",12f,MUTED))
        val bars=row().apply{gravity=Gravity.BOTTOM}
        val rows=db.rows().filter{it.kind=="EXPENSE"}
        val cats=listOf("Food","Housing","Shopping","Bills","Other")
        cats.forEachIndexed{idx,c->
            val amount=rows.filter{it.raw.contains("category=$c",true)||it.name.contains(c,true)}.sumOf{it.amount}
            val col=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
            val bar=View(this@MainActivity).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(if(idx==0)LIME else Color.rgb(61,83,74));cornerRadius=dp(5).toFloat()}}
            col.addView(bar,LinearLayout.LayoutParams(dp(24),dp((24+idx*11).coerceAtMost(72))))
            col.addView(text(c,9f,MUTED))
            bars.addView(col,LinearLayout.LayoutParams(0,dp(100),1f).apply{gravity=Gravity.BOTTOM})
        }
        insights.addView(bars);addPanel(insights)
        body.addView(text("RECENT ACTIVITY",12f,MUTED,true));space(6)
        val latest=panel(12)
        db.rows().take(5).forEach{r->transactionRow(latest,r)}
        if(db.count()==0) latest.addView(text("No transactions yet. Add one or import eligible SMS alerts.",13f,MUTED))
        addPanel(latest)
    }
    private fun transactionRow(container:LinearLayout,r:TxDb.Row){
        val line=row().apply{setPadding(0,dp(10),0,dp(10))}
        val icon=text(if(r.kind=="INCOME")"↙" else "↗",20f,if(r.kind=="INCOME")LIME else TEXT,true).apply{gravity=Gravity.CENTER;background=android.graphics.drawable.GradientDrawable().apply{setColor(PANEL2);cornerRadius=dp(12).toFloat()}}
        line.addView(icon,LinearLayout.LayoutParams(dp(42),dp(42)).apply{marginEnd=dp(10)})
        val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        info.addView(text(r.name,14f,TEXT,true));info.addView(text("${r.source} · "+SimpleDateFormat("dd MMM",Locale.getDefault()).format(Date(r.date)),10f,MUTED))
        line.addView(info,LinearLayout.LayoutParams(0,-2,1f))
        line.addView(text((if(r.kind=="INCOME")"+" else "−")+money(r.amount),13f,if(r.kind=="INCOME")LIME else TEXT,true))
        line.setOnClickListener{showDetails(r)}
        container.addView(line);container.addView(View(this).apply{setBackgroundColor(Color.rgb(42,53,60))},LinearLayout.LayoutParams(-1,dp(1)))
    }
    private fun activity(){
        space(8);body.addView(text("Transactions",26f,TEXT,true));body.addView(text("A single timeline for your money.",13f,MUTED));space(12)
        val search=EditText(this).apply{hint="Search merchant or source";setTextColor(TEXT);setHintTextColor(MUTED);textSize=14f;setSingleLine(true);setPadding(dp(12),0,dp(12),0);background=android.graphics.drawable.GradientDrawable().apply{setColor(PANEL);cornerRadius=dp(12).toFloat()}}
        search.setText(searchText);search.addTextChangedListener(object:android.text.TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){searchText=s?.toString()?: "";render()}
            override fun afterTextChanged(s:android.text.Editable?){}
        })
        addPanel(search)
        val filters=row()
        listOf("All","INCOME","EXPENSE").forEach{f->filters.addView(btn(if(f=="INCOME")"Money in" else if(f=="EXPENSE")"Money out" else "All", {selectedFilter=f;render()},selectedFilter==f),LinearLayout.LayoutParams(0,dp(42),1f).apply{marginEnd=dp(4)})}
        addPanel(filters)
        val list=panel(12)
        db.rows().filter{(selectedFilter=="All"||it.kind==selectedFilter)&&(it.name+" "+it.source+" "+it.raw).contains(searchText,true)}.forEach{transactionRow(list,it)}
        addPanel(list)
        addPanel(btn("+ Add transaction",{showAdd()},true))
        addPanel(btn("Import from SMS inbox",{importSms()}))
    }
    private fun insights(){
        space(8);body.addView(text("Insights",26f,TEXT,true));body.addView(text("Your money, made visible.",13f,MUTED));space(12)
        val (inc,out)=db.totals()
        val p=panel();p.addView(text("NET CASH FLOW",11f,MUTED,true));p.addView(text(money(inc-out),28f,TEXT,true));p.addView(text("Income minus recorded expenses",12f,MUTED));addPanel(p)
        val c=panel();c.addView(text("EXPENSE BREAKDOWN",12f,MUTED,true))
        val rows=db.rows().filter{it.kind=="EXPENSE"}
        val cats=listOf("Food","Housing","Shopping","Bills","Travel","Other")
        cats.forEachIndexed{idx,cat->
            val value=rows.filter{it.raw.contains("category=$cat",true)}.sumOf{it.amount}
            if(value>0){
                c.addView(text("$cat                         ${money(value)}",13f,TEXT))
                val bar=View(this).apply{background=android.graphics.drawable.GradientDrawable().apply{setColor(if(idx==0)LIME else Color.rgb(75,117,98));cornerRadius=dp(5).toFloat()}}
                c.addView(bar,LinearLayout.LayoutParams(-1,dp(7)).apply{bottomMargin=dp(10)})
                bar.layoutParams.width=dp((value/out.coerceAtLeast(1.0)*280).toInt().coerceIn(20,280))
            }
        }
        c.addView(text("Categorization improves insight accuracy. Imported SMS records should be reviewed.",11f,MUTED));addPanel(c)
    }
    private fun accounts(){
        space(8);body.addView(text("Sources & accounts",26f,TEXT,true));body.addView(text("Payment channels found in your records.",13f,MUTED));space(12)
        listOf("HDFC Bank","Google Pay","PhonePe","Paytm","Credit card","Cash").forEach{src->
            val rows=db.rows().filter{it.source.equals(src,true)}
            val card=panel(14);val r=row()
            r.addView(text("◈",22f,LIME,true),LinearLayout.LayoutParams(dp(38),dp(40)))
            val info=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
            info.addView(text(src,15f,TEXT,true));info.addView(text("${rows.size} recorded transaction(s)",11f,MUTED))
            r.addView(info,LinearLayout.LayoutParams(0,-2,1f))
            r.addView(text(money(rows.sumOf{if(it.kind=="INCOME")it.amount else -it.amount}),12f,TEXT,true))
            card.addView(r);addPanel(card)
        }
        addPanel(btn("Import bank SMS",{importSms()},true))
    }
    private fun settings(){
        space(8);body.addView(text("Settings",26f,TEXT,true));body.addView(text("Privacy-first by design.",13f,MUTED));space(12)
        val c=panel()
        c.addView(text("LOCAL STORAGE",11f,LIME,true));c.addView(text("Transactions are stored in the app's private SQLite database on this device.",14f,TEXT));space(12)
        c.addView(text("SMS PERMISSION",11f,LIME,true));c.addView(text("SMS import only runs after you tap the import button and grant permission. This starter does not run a background SMS listener.",13f,MUTED));space(12)
        c.addView(text("IMPORTANT",11f,RED,true));c.addView(text("SMS parsing is heuristic. Check each imported amount and direction. Do not use this app as an authoritative bank balance.",13f,MUTED))
        addPanel(c)
        addPanel(btn("Clear all saved transactions",{android.app.AlertDialog.Builder(this).setTitle("Delete all transactions?").setMessage("This permanently removes the records stored in this app.").setNegativeButton("Cancel",null).setPositiveButton("Delete"){_,_->db.writableDatabase.delete("transactions",null,null);render()}.show()}))
    }
    private fun showAdd(){
        val form=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),dp(12),dp(20),dp(4))}
        val name=EditText(this).apply{hint="Description";setTextColor(TEXT);setHintTextColor(MUTED)}
        val amount=EditText(this).apply{hint="Amount in ₹";inputType=2;setTextColor(TEXT);setHintTextColor(MUTED)}
        val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Expense","Income"))}
        val cat=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Food","Housing","Shopping","Bills","Travel","Fitness","Transfer","Other"))}
        val src=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,listOf("Google Pay","PhonePe","Paytm","HDFC Bank","Credit card","Cash"))}
        form.addView(name);form.addView(amount);form.addView(text("Type",12f,MUTED));form.addView(type);form.addView(text("Category",12f,MUTED));form.addView(cat);form.addView(text("Source",12f,MUTED));form.addView(src)
        android.app.AlertDialog.Builder(this).setTitle("New transaction").setView(form).setNegativeButton("Cancel",null).setPositiveButton("Save"){_,_->
            val n=name.text.toString().trim();val a=amount.text.toString().toDoubleOrNull()
            if(n.isBlank()||a==null||a<=0){Toast.makeText(this,"Enter a description and valid amount",Toast.LENGTH_LONG).show()}
            else {db.manual(System.currentTimeMillis(),n,a,if(type.selectedItem=="Income")"INCOME" else "EXPENSE",src.selectedItem.toString(),cat.selectedItem.toString());render()}
        }.show()
    }
    private fun showDetails(r:TxDb.Row){
        android.app.AlertDialog.Builder(this).setTitle(r.name).setMessage("${money(r.amount)}\n${r.kind}\n${r.source}\n"+SimpleDateFormat("dd MMM yyyy, HH:mm",Locale.getDefault()).format(Date(r.date))+"\n\n"+r.raw)
            .setNegativeButton("Close",null).setNeutralButton("Delete"){_,_->db.delete(r.id);render()}.show()
    }
    private fun importSms(){
        if(checkSelfPermission(Manifest.permission.READ_SMS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.READ_SMS),100);return}
        scanInbox()
    }
    override fun onRequestPermissionsResult(requestCode:Int,permissions:Array<out String>,grantResults:IntArray){
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==100&&grantResults.isNotEmpty()&&grantResults[0]==PackageManager.PERMISSION_GRANTED)scanInbox()
        else Toast.makeText(this,"SMS permission not granted.",Toast.LENGTH_LONG).show()
    }
    private fun scanInbox(){
        var imported=0;var skipped=0
        try{
            val projection=arrayOf(Telephony.Sms._ID,Telephony.Sms.ADDRESS,Telephony.Sms.BODY,Telephony.Sms.DATE)
            contentResolver.query(Telephony.Sms.Inbox.CONTENT_URI,projection,null,null,"${Telephony.Sms.DATE} DESC")?.use{c->
                while(c.moveToNext()){
                    val id=c.getString(c.getColumnIndexOrThrow(Telephony.Sms._ID))
                    val address=c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))?:"Bank SMS"
                    val body=c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY))?:continue
                    val date=c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val p=parse(body)?:continue
                    if(db.add("sms:$id",date,p.first,p.second,p.third,address,body))imported++ else skipped++
                }
            }
            Toast.makeText(this,"Imported $imported; duplicates skipped $skipped. Review the records.",Toast.LENGTH_LONG).show();render()
        }catch(e:Exception){Toast.makeText(this,"Could not import SMS: ${e.message}",Toast.LENGTH_LONG).show()}
    }
    private fun parse(body:String):Triple<String,Double,String>?{
        if(Regex("(?i)\\b(otp|one.time.password|verification code)\\b").containsMatchIn(body))return null
        if(!Regex("(?i)\\b(upi|debited|credited|sent|received|paid|payment|transferred|transaction|a/c|account)\\b").containsMatchIn(body))return null
        val amount=Regex("(?i)(?:rs\\.?\\s*|inr\\s*|₹\\s*)([0-9,]+(?:\\.[0-9]{1,2})?)").find(body)?.groupValues?.get(1)?.replace(",","")?.toDoubleOrNull()?:return null
        val kind=when{Regex("(?i)\\b(credited|received)\\b").containsMatchIn(body)->"INCOME";Regex("(?i)\\b(debited|paid|sent)\\b").containsMatchIn(body)->"EXPENSE";else->return null}
        val party=Regex("(?i)\\b(?:to|from)\\s+([A-Za-z0-9@._ -]{2,28})").find(body)?.groupValues?.get(1)?.trim()
        return Triple(party?.takeIf{it.isNotBlank()}?:if(kind=="INCOME")"Money received — review" else "Payment — review",amount,kind)
    }
}
