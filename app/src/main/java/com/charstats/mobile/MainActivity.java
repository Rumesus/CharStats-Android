package com.charstats.mobile;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_FILES=1001, REQ_FOLDER=1002;
    private EditText rate;
    private CheckBox langUkr,langRus,langEng,ocrAllPdf;
    private ProgressBar progress;
    private TextView total,previewText;
    private ImageView previewImage;
    private ListView list;
    private ArrayAdapter<ResultItem> adapter;
    private final ArrayList<ResultItem> results=new ArrayList<>();
    private final ExecutorService pool=Executors.newSingleThreadExecutor();
    private FileProcessor processor;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(com.charstats.mobile.R.layout.activity_main);
        rate=findViewById(R.id.rate); langUkr=findViewById(R.id.langUkr); langRus=findViewById(R.id.langRus);
        langEng=findViewById(R.id.langEng); ocrAllPdf=findViewById(R.id.ocrAllPdf);
        progress=findViewById(R.id.progress); total=findViewById(R.id.total);
        previewText=findViewById(R.id.previewText); previewImage=findViewById(R.id.previewImage); list=findViewById(R.id.list);
        processor=new FileProcessor(this);
        adapter=new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, results);
        list.setAdapter(adapter);

        findViewById(R.id.pickFiles).setOnClickListener(v->pickFiles());
        findViewById(R.id.pickFolder).setOnClickListener(v->pickFolder());
        findViewById(R.id.clear).setOnClickListener(v->{ results.clear(); adapter.notifyDataSetChanged(); updateTotal(); previewImage.setImageDrawable(null); previewText.setText("Попередній перегляд"); });
        findViewById(R.id.exportExcel).setOnClickListener(v->exportExcel());
        findViewById(R.id.clientPdf).setOnClickListener(v->clientPdf(false));
        findViewById(R.id.sharePdf).setOnClickListener(v->clientPdf(true));
        list.setOnItemClickListener((p,v,pos,id)->preview(results.get(pos)));
        list.setOnItemLongClickListener((p,v,pos,id)->{ openOriginal(results.get(pos)); return true; });
    }

    private void pickFiles() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*"); i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true); i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i,REQ_FILES);
    }
    private void pickFolder() { startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),REQ_FOLDER); }

    @Override protected void onActivityResult(int req,int res,Intent data) {
        super.onActivityResult(req,res,data);
        if(res!=RESULT_OK||data==null)return;
        if(req==REQ_FILES){
            ArrayList<WorkFile> fs=new ArrayList<>();
            if(data.getClipData()!=null) for(int k=0;k<data.getClipData().getItemCount();k++){
                Uri u=data.getClipData().getItemAt(k).getUri(); DocumentFile f=DocumentFile.fromSingleUri(this,u);
                fs.add(new WorkFile(u,f!=null?f.getName():"файл","Окремі файли\\"+(f!=null?f.getName():"файл")));
            } else if(data.getData()!=null){
                Uri u=data.getData(); DocumentFile f=DocumentFile.fromSingleUri(this,u);
                fs.add(new WorkFile(u,f!=null?f.getName():"файл","Окремі файли\\"+(f!=null?f.getName():"файл")));
            }
            process(fs);
        } else if(req==REQ_FOLDER) {
            Uri tree=data.getData();
            getContentResolver().takePersistableUriPermission(tree, data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION));
            DocumentFile root=DocumentFile.fromTreeUri(this,tree);
            ArrayList<WorkFile> fs=new ArrayList<>();
            if(root!=null) collect(root, root.getName()==null?"Робота":root.getName(), fs);
            process(fs);
        }
    }

    private void collect(DocumentFile dir,String rel,ArrayList<WorkFile> out){
        for(DocumentFile f:dir.listFiles()){
            if(f.isDirectory()) collect(f,rel+"\\"+f.getName(),out);
            else if(f.isFile()&&supported(f.getName())) out.add(new WorkFile(f.getUri(),f.getName(),rel+"\\"+f.getName()));
        }
    }
    private boolean supported(String n){ if(n==null)return false; String s=n.toLowerCase(Locale.ROOT); return s.matches(".*\\.(txt|doc|docx|xls|xlsx|pdf|jpg|jpeg|png|bmp|webp|tif|tiff)$"); }
    private String langs(){ ArrayList<String>a=new ArrayList<>(); if(langUkr.isChecked())a.add("ukr"); if(langRus.isChecked())a.add("rus"); if(langEng.isChecked())a.add("eng"); return a.isEmpty()?"eng":String.join("+",a); }
    private double tariff(){ try{return Double.parseDouble(rate.getText().toString().replace(',','.'));}catch(Exception e){return 0;} }

    private void process(ArrayList<WorkFile> fs){
        if(fs.isEmpty())return;
        progress.setProgress(0); final String languages=langs(); final double t=tariff(); final boolean all=ocrAllPdf.isChecked();
        pool.execute(()->{
            int done=0;
            for(WorkFile f:fs){
                ResultItem item;
                try{
                    String text=processor.read(f.uri,f.name,languages,all);
                    long ws=text.length(), wo=text.replaceAll("\\s","").length();
                    double p15=ws*1.15, pages=p15/1800.0, cost=pages*t;
                    item=new ResultItem(f.uri,f.path,f.name,ws,wo,p15,pages,cost,null);
                }catch(Exception e){ item=new ResultItem(f.uri,f.path,f.name,0,0,0,0,0,e.getMessage()); }
                ResultItem fi=item; int percent=(++done)*100/fs.size();
                runOnUiThread(()->{ results.add(fi); adapter.notifyDataSetChanged(); progress.setProgress(percent); updateTotal(); });
            }
        });
    }

    private void updateTotal(){
        double p=0,c=0; long w=0,wo=0;
        for(ResultItem r:results) if(r.error==null){w+=r.withSpaces;wo+=r.withoutSpaces;p+=r.pages;c+=r.cost;}
        total.setText("Разом: "+results.size()+" файлів | з пробілами "+w+" | без пробілів "+wo+" | сторінки "+String.format("%.2f",p)+" | "+String.format("%.2f",c));
    }

    private void preview(ResultItem r){
        previewText.setText(r.path+"\n\nДовге натискання — відкрити оригінал.");
        pool.execute(()->{
            try{
                Bitmap b=processor.previewBitmap(r.uri,r.name);
                runOnUiThread(()->{ if(b!=null)previewImage.setImageBitmap(b); else previewImage.setImageDrawable(null); });
            }catch(Exception ignored){}
        });
    }
    private void openOriginal(ResultItem r){ Intent i=new Intent(Intent.ACTION_VIEW); i.setDataAndType(r.uri,getContentResolver().getType(r.uri)); i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); try{startActivity(i);}catch(Exception e){Toast.makeText(this,"Немає програми для відкриття",Toast.LENGTH_SHORT).show();} }

    private void exportExcel(){ pool.execute(()->{try{File f=ReportWriter.writeExcel(this,results);runOnUiThread(()->share(f,"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));}catch(Exception e){toast(e.getMessage());}}); }
    private void clientPdf(boolean share){ pool.execute(()->{try{File f=ReportWriter.writeClientPdf(this,results);runOnUiThread(()->{if(share)share(f,"application/pdf");else openFile(f,"application/pdf");});}catch(Exception e){toast(e.getMessage());}}); }

    private void share(File f,String mime){ Uri u=FileProvider.getUriForFile(this,getPackageName()+".files",f); Intent i=new Intent(Intent.ACTION_SEND);i.setType(mime);i.putExtra(Intent.EXTRA_STREAM,u);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поділитися")); }
    private void openFile(File f,String mime){ Uri u=FileProvider.getUriForFile(this,getPackageName()+".files",f); Intent i=new Intent(Intent.ACTION_VIEW);i.setDataAndType(u,mime);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);try{startActivity(i);}catch(Exception e){share(f,mime);} }
    private void toast(String s){ runOnUiThread(()->Toast.makeText(this,s==null?"Помилка":s,Toast.LENGTH_LONG).show()); }

    static class WorkFile{final Uri uri;final String name,path;WorkFile(Uri u,String n,String p){uri=u;name=n;path=p;}}
}
