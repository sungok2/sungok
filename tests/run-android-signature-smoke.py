# Diagnostic only: hand-written mock API signatures, NOT Android SDK validation.
from pathlib import Path
import tempfile,subprocess
root=Path(__file__).resolve().parent.parent
stubs={
'android/R.java': '''package android; public final class R {public static final class layout {public static final int simple_spinner_item=1,simple_spinner_dropdown_item=2;}}''',
'android/app/Activity.java': '''package android.app; import android.content.*; import android.os.*; import android.view.View; import android.content.pm.PackageManager; import android.content.res.*;
public class Activity extends Context { protected void onCreate(Bundle b){} protected void onStart(){} protected void onResume(){} protected void onStop(){} protected void onDestroy(){} protected void onSaveInstanceState(Bundle b){} protected void onActivityResult(int r,int c,Intent i){} public static final int RESULT_OK=-1; public void setContentView(View v){} public void startActivityForResult(Intent i,int n){} public void runOnUiThread(Runnable r){} public boolean isDestroyed(){return false;} public PackageManager getPackageManager(){return null;} public Resources getResources(){return null;} public android.content.res.AssetManager getAssets(){return null;} public ContentResolver getContentResolver(){return null;} }''',
'android/app/PendingIntent.java': '''package android.app; import android.content.*; public class PendingIntent {public static final int FLAG_MUTABLE=0x02000000,FLAG_UPDATE_CURRENT=0x08000000; public static PendingIntent getBroadcast(Context c,int i,Intent it,int f){return null;}}''',
'android/app/AlertDialog.java': '''package android.app; import android.content.*; public class AlertDialog {public static class Builder {public Builder(Context c){} public Builder setTitle(String s){return this;}public Builder setMessage(String s){return this;}public Builder setNegativeButton(String s,DialogInterface.OnClickListener l){return this;}public Builder setPositiveButton(String s,DialogInterface.OnClickListener l){return this;}public AlertDialog show(){return null;}}}''',
'android/content/DialogInterface.java': '''package android.content; public interface DialogInterface {interface OnClickListener {void onClick(DialogInterface dialog,int which);}}''',
'android/content/Context.java': '''package android.content; public class Context {public static final String USB_SERVICE="usb", PRINT_SERVICE="print"; public static final int RECEIVER_NOT_EXPORTED=4; public Object getSystemService(String s){return null;}public Context getApplicationContext(){return this;}public java.io.File getCacheDir(){return null;}public ContentResolver getContentResolver(){return null;} public Intent registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){return null;} public Intent registerReceiver(BroadcastReceiver r,IntentFilter f){return null;} public void unregisterReceiver(BroadcastReceiver r){} public String getPackageName(){return "com.galaxyuniversalio";}public void sendBroadcast(Intent i){}}''',
'android/content/BroadcastReceiver.java': '''package android.content; public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}''',
'android/content/Intent.java': '''package android.content; import android.net.Uri; public class Intent {public static final String ACTION_CREATE_DOCUMENT="",ACTION_OPEN_DOCUMENT="",CATEGORY_OPENABLE="",EXTRA_TITLE=""; public Intent(String s){} public Intent(Context c,Class<?> cl){} public String getAction(){return null;}public String getStringExtra(String s){return null;} public boolean getBooleanExtra(String s, boolean fallback){return false;}public <T> T getParcelableExtra(String s,Class<T> cl){return null;} public <T> T getParcelableExtra(String s){return null;}public Intent setAction(String s){return this;} public Intent setPackage(String s){return this;}public Intent putExtra(String s,boolean b){return this;}public Intent putExtra(String s,String v){return this;}public Intent addCategory(String s){return this;} public Intent setType(String s){return this;}public Uri getData(){return null;}}''',
'android/content/IntentFilter.java': '''package android.content; public class IntentFilter {public void addAction(String s){}}''',
'android/content/ContentResolver.java': '''package android.content; import android.net.Uri;import android.database.Cursor;import java.io.*; public class ContentResolver {public OutputStream openOutputStream(Uri uri,String mode)throws FileNotFoundException{return null;} public InputStream openInputStream(Uri uri)throws FileNotFoundException{return null;}public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sortOrder){return null;}}''',
'android/content/pm/PackageManager.java': '''package android.content.pm;public class PackageManager {public static final String FEATURE_USB_HOST="usb.host";public boolean hasSystemFeature(String f){return false;}}''',
'android/content/res/AssetManager.java': '''package android.content.res; import java.io.*;public class AssetManager{public InputStream open(String f)throws IOException{return null;}}''',
'android/content/res/Resources.java': '''package android.content.res; import android.util.DisplayMetrics; public class Resources { public DisplayMetrics getDisplayMetrics(){return null;}}''',
'android/util/DisplayMetrics.java': '''package android.util;public class DisplayMetrics{public float density;}''',
'android/database/Cursor.java': '''package android.database; public interface Cursor extends AutoCloseable {boolean moveToFirst();int getColumnIndex(String c);String getString(int c);void close();}''',
'android/provider/OpenableColumns.java': '''package android.provider; public interface OpenableColumns {String DISPLAY_NAME="_display_name", SIZE="_size";}''',
'android/net/Uri.java': '''package android.net;public class Uri{}''',
'android/os/Build.java': '''package android.os; public class Build {public static final String MANUFACTURER="",MODEL=""; public static class VERSION{public static final int SDK_INT=35;}}''',
'android/os/Bundle.java': '''package android.os;import java.util.HashMap;public class Bundle {public String getString(String k){return null;}public int getInt(String k){return 0;}public boolean getBoolean(String k,boolean d){return false;}public void putString(String k,String v){}public void putInt(String k,int v){}public void putBoolean(String k,boolean v){}}''',
'android/view/View.java': '''package android.view;public class View {public interface OnClickListener{void onClick(View v);}public void setPadding(int a,int b,int c,int d){} public void setContentDescription(CharSequence c){}}''',
'android/widget/TextView.java': '''package android.widget;import android.content.*;import android.view.*;public class TextView extends View{public TextView(Context c){} public void setText(String s){}public void setTextSize(float f){}public void setTextColor(int c){} public void setTextIsSelectable(boolean b){}}''',
'android/widget/Button.java': '''package android.widget;import android.content.*; public class Button extends TextView {public Button(Context c){super(c);}public void setOnClickListener(android.view.View.OnClickListener l){}public void setEnabled(boolean b){}}''',
'android/widget/CompoundButton.java': '''package android.widget;import android.content.*;public class CompoundButton extends Button {public CompoundButton(Context c){super(c);} public interface OnCheckedChangeListener{void onCheckedChanged(CompoundButton b,boolean on);}public void setOnCheckedChangeListener(OnCheckedChangeListener l){}public void setChecked(boolean b){} public boolean isChecked(){return false;}}''',
'android/widget/CheckBox.java': '''package android.widget;import android.content.*;public class CheckBox extends CompoundButton{public CheckBox(Context c){super(c);}}''',
'android/text/InputType.java': 'package android.text;public final class InputType{public static final int TYPE_CLASS_TEXT=1;}',
'android/text/Editable.java': 'package android.text;public interface Editable extends CharSequence{}',
'android/widget/EditText.java': 'package android.widget;import android.content.*;import android.text.*;public class EditText extends TextView {public EditText(Context c){super(c);}public void setSingleLine(boolean b){}public void setInputType(int i){}public void setHint(String s){}public Editable getText(){return null;}}',
'android/graphics/Bitmap.java': 'package android.graphics;public class Bitmap{public int getWidth(){return 1;}public int getHeight(){return 1;}public void recycle(){}}',
'android/graphics/BitmapFactory.java': 'package android.graphics;public class BitmapFactory{public static class Options{public boolean inJustDecodeBounds;public int outWidth,outHeight,inSampleSize;}public static Bitmap decodeByteArray(byte[] b,int off,int len,Options o){return null;}}',
'android/graphics/Canvas.java': 'package android.graphics;public class Canvas{public void drawColor(int c){}public void drawBitmap(Bitmap b,Rect src,RectF dst,Paint p){}}',
'android/graphics/Rect.java': 'package android.graphics;public class Rect{public Rect(int a,int b,int c,int d){}}',
'android/graphics/RectF.java': 'package android.graphics;public class RectF{public RectF(float a,float b,float c,float d){}}',
'android/graphics/Paint.java': 'package android.graphics;public class Paint{public static final int FILTER_BITMAP_FLAG=2;public Paint(int flags){}}',
'android/graphics/pdf/PdfDocument.java': 'package android.graphics.pdf;import android.graphics.*;import java.io.*;public class PdfDocument{public static class PageInfo{public static class Builder{public Builder(int w,int h,int n){}public PageInfo create(){return null;}}}public static class Page{public Canvas getCanvas(){return null;}}public Page startPage(PageInfo p){return null;}public void finishPage(Page p){}public void writeTo(OutputStream o)throws IOException{}public void close(){}}',
'android/widget/LinearLayout.java': '''package android.widget; import android.content.*; import android.view.*;public class LinearLayout extends View {public static final int HORIZONTAL=0,VERTICAL=1;public LinearLayout(Context c){}public void setOrientation(int v){}public void addView(View v){}public void setFillViewport(boolean b){}public void addView(View v, LayoutParams p){}public static class LayoutParams {public LayoutParams(int a,int b){}public LayoutParams(int a,int b,float w){}}}''',
'android/widget/ScrollView.java': '''package android.widget;import android.content.*;import android.view.*;public class ScrollView extends View {public ScrollView(Context c){} public void addView(View v){}public void setFillViewport(boolean b){}}''',
'android/widget/AdapterView.java': '''package android.widget;import android.view.*;public class AdapterView<T> extends View {public interface OnItemSelectedListener{void onItemSelected(AdapterView<?> parent,View view,int position,long id); void onNothingSelected(AdapterView<?> parent);}}''',
'android/widget/Spinner.java': '''package android.widget;import android.content.*;public class Spinner extends AdapterView<Object>{public Spinner(Context c){}public void setOnItemSelectedListener(OnItemSelectedListener l){}public int getSelectedItemPosition(){return 0;}public Object getSelectedItem(){return null;}public void setAdapter(ArrayAdapter<String> a){}public void setSelection(int i){}}''',
'android/widget/ArrayAdapter.java': '''package android.widget;import android.content.*;import java.util.*;public class ArrayAdapter<T>{public ArrayAdapter(Context c,int layout,List<T> a){}public void setDropDownViewResource(int res){}}''',
'android/hardware/usb/UsbConstants.java': '''package android.hardware.usb; public final class UsbConstants {public static final int USB_ENDPOINT_XFER_BULK=2,USB_DIR_OUT=0,USB_DIR_IN=128;}''',
'android/hardware/usb/UsbManager.java': '''package android.hardware.usb;import android.app.PendingIntent;import java.util.*;public class UsbManager{public static final String ACTION_USB_DEVICE_ATTACHED="attached",ACTION_USB_DEVICE_DETACHED="detached",EXTRA_PERMISSION_GRANTED="perm",EXTRA_DEVICE="dev";public Map<String,UsbDevice> getDeviceList(){return null;}public boolean hasPermission(UsbDevice d){return false;}public void requestPermission(UsbDevice d,PendingIntent p){}public UsbDeviceConnection openDevice(UsbDevice d){return null;}}''',
'android/hardware/usb/UsbDevice.java': '''package android.hardware.usb; public class UsbDevice {public int getDeviceId(){return 0;}public int getVendorId(){return 0;}public int getProductId(){return 0;}public String getDeviceName(){return null;}public int getDeviceClass(){return 0;}public int getDeviceSubclass(){return 0;}public int getDeviceProtocol(){return 0;}public int getInterfaceCount(){return 0;}public UsbInterface getInterface(int i){return null;}public String getManufacturerName(){return null;}public String getProductName(){return null;}public String getSerialNumber(){return null;}}''',
'android/hardware/usb/UsbInterface.java': '''package android.hardware.usb; public class UsbInterface {public int getId(){return 0;}public int getAlternateSetting(){return 0;}public int getInterfaceClass(){return 0;}public int getInterfaceSubclass(){return 0;}public int getInterfaceProtocol(){return 0;}public int getEndpointCount(){return 0;}public UsbEndpoint getEndpoint(int n){return null;}}''',
'android/hardware/usb/UsbEndpoint.java': '''package android.hardware.usb;public class UsbEndpoint {public int getAddress(){return 0;}public int getDirection(){return 0;}public int getType(){return 0;}public int getMaxPacketSize(){return 0;}public int getInterval(){return 0;}}''',
'android/hardware/usb/UsbDeviceConnection.java': '''package android.hardware.usb;public class UsbDeviceConnection{public boolean claimInterface(UsbInterface intf,boolean force){return false;}public boolean setInterface(UsbInterface intf){return false;}public boolean releaseInterface(UsbInterface intf){return false;}public void close(){}public int controlTransfer(int type,int request,int value,int index,byte[] buffer,int length,int timeout){return -1;}public int bulkTransfer(UsbEndpoint ep,byte[] buffer,int offset,int length,int timeout){return -1;}}''',
}
stubs.update({
'android/os/CancellationSignal.java': '''package android.os;public class CancellationSignal {public boolean isCanceled(){return false;}}''',
'android/os/ParcelFileDescriptor.java': '''package android.os;import java.io.*;public class ParcelFileDescriptor implements Closeable {public void close()throws IOException{} public static class AutoCloseOutputStream extends OutputStream{public AutoCloseOutputStream(ParcelFileDescriptor fd){}public void write(int b){}public void write(byte[] b,int off,int len){}public void close()throws IOException{}}}''',
'android/print/PrintAttributes.java': '''package android.print;public class PrintAttributes {public static class Builder{public PrintAttributes build(){return new PrintAttributes();}}}''',
'android/print/PageRange.java': '''package android.print;public class PageRange{public static final PageRange ALL_PAGES=new PageRange();}''',
'android/print/PrintDocumentInfo.java': '''package android.print;public class PrintDocumentInfo{public static final int CONTENT_TYPE_DOCUMENT=0,PAGE_COUNT_UNKNOWN=-1;public static class Builder{public Builder(String s){}public Builder setContentType(int i){return this;}public Builder setPageCount(int i){return this;}public PrintDocumentInfo build(){return new PrintDocumentInfo();}}}''',
'android/print/PrintDocumentAdapter.java': '''package android.print;import android.os.*;public abstract class PrintDocumentAdapter{public abstract void onLayout(PrintAttributes a,PrintAttributes b,CancellationSignal c,LayoutResultCallback d,Bundle e);public abstract void onWrite(PageRange[] p,ParcelFileDescriptor d,CancellationSignal s,WriteResultCallback c);public void onFinish(){}public static abstract class LayoutResultCallback{public void onLayoutCancelled(){}public void onLayoutFinished(PrintDocumentInfo d,boolean changed){}}public static abstract class WriteResultCallback{public void onWriteCancelled(){}public void onWriteFinished(PageRange[] p){}public void onWriteFailed(CharSequence s){}}}''',
'android/print/PrintManager.java': '''package android.print;public class PrintManager{public void print(String name,PrintDocumentAdapter adapter,PrintAttributes attrs){}}''',
})

# M0.9 SAF URI/Document provider signatures. This is a mock, not android.jar.
stubs.update({
'android/R.java': """package android; public final class R {
 public static final class layout {public static final int simple_spinner_item=1,simple_spinner_dropdown_item=2;}
 public static final class attr {public static final int progressBarStyleHorizontal=1;}
}""",
'android/app/Activity.java': """package android.app;
import android.content.*;import android.os.*;import android.view.View;import android.content.pm.PackageManager;import android.content.res.*;
public class Activity extends Context {
 protected void onCreate(Bundle b){} protected void onStart(){} protected void onResume(){}
 protected void onStop(){} protected void onDestroy(){} protected void onSaveInstanceState(Bundle b){}
 protected void onActivityResult(int r,int c,Intent i){}public void onBackPressed(){}
 public static final int RESULT_OK=-1;public void setContentView(View v){}
 public void startActivityForResult(Intent i,int n){}public void startActivity(Intent i){}
 public void runOnUiThread(Runnable r){}public boolean isDestroyed(){return false;}
 public PackageManager getPackageManager(){return null;}public Resources getResources(){return null;}
 public android.content.res.AssetManager getAssets(){return null;}
 public ContentResolver getContentResolver(){return null;}
}""",
'android/content/Intent.java': """package android.content;import android.net.Uri;
public class Intent {
 public static final String ACTION_CREATE_DOCUMENT="",ACTION_OPEN_DOCUMENT="",ACTION_OPEN_DOCUMENT_TREE="",CATEGORY_OPENABLE="",EXTRA_TITLE="",EXTRA_ALLOW_MULTIPLE="";
 public static final int FLAG_GRANT_READ_URI_PERMISSION=1,FLAG_GRANT_WRITE_URI_PERMISSION=2,
 FLAG_GRANT_PERSISTABLE_URI_PERMISSION=64,FLAG_GRANT_PREFIX_URI_PERMISSION=128;
 public Intent(String s){}public Intent(Context c,Class<?> cl){}
 public String getAction(){return null;}public String getStringExtra(String s){return null;}
 public boolean getBooleanExtra(String s,boolean fallback){return false;}
 public <T> T getParcelableExtra(String s,Class<T> cl){return null;}
 public <T> T getParcelableExtra(String s){return null;}
 public Intent setAction(String s){return this;}public Intent setPackage(String s){return this;}
 public Intent putExtra(String s,boolean b){return this;}public Intent putExtra(String s,String v){return this;}
 public Intent addCategory(String s){return this;}public Intent setType(String s){return this;}
 public Intent addFlags(int flags){return this;}public int getFlags(){return 0;}
 public Uri getData(){return null;}public ClipData getClipData(){return null;}
}""",
'android/content/ClipData.java': """package android.content;import android.net.Uri;
public class ClipData {public int getItemCount(){return 0;}
 public Item getItemAt(int i){return null;}
 public static class Item {public Uri getUri(){return null;}}
}""",
'android/content/ContentResolver.java': """package android.content;
import android.net.Uri;import android.database.Cursor;import java.io.*;
public class ContentResolver {
 public OutputStream openOutputStream(Uri u,String mode)throws FileNotFoundException{return null;}
 public InputStream openInputStream(Uri u)throws FileNotFoundException{return null;}
 public Cursor query(Uri u,String[] p,String s,String[] a,String so){return null;}
 public String getType(Uri u){return null;}
 public void takePersistableUriPermission(Uri u,int flags){}
}""",
'android/database/Cursor.java': """package android.database;
public interface Cursor extends AutoCloseable {
 boolean moveToFirst();boolean moveToNext();int getColumnIndex(String c);
 String getString(int c);int getInt(int c);long getLong(int c);boolean isNull(int c);
 void close();
}""",
'android/net/Uri.java': """package android.net;
public class Uri {public static Uri parse(String s){return null;}
 public String getLastPathSegment(){return null;}
 public String toString(){return "";}
}""",
'android/os/Bundle.java': """package android.os;import java.util.*;
public class Bundle {
 public String getString(String k){return null;}public int getInt(String k){return 0;}
 public boolean getBoolean(String k,boolean d){return false;}
 public void putString(String k,String v){}public void putInt(String k,int v){}
 public void putBoolean(String k,boolean v){}
 public ArrayList<String> getStringArrayList(String k){return null;}
 public void putStringArrayList(String k,ArrayList<String> a){}
}""",
'android/view/View.java': """package android.view;
public class View {public static final int GONE=8,VISIBLE=0;
 public interface OnClickListener{void onClick(View v);}
 public void setPadding(int a,int b,int c,int d){}
 public void setContentDescription(CharSequence c){}
 public void setVisibility(int v){}
}""",
'android/widget/Button.java': """package android.widget;import android.content.*;
public class Button extends TextView {public Button(Context c){super(c);}
 public void setOnClickListener(android.view.View.OnClickListener l){}
 public void setEnabled(boolean b){}public void setAllCaps(boolean b){}
}""",
'android/widget/ProgressBar.java': """package android.widget;import android.content.*;
public class ProgressBar extends android.view.View {
 public ProgressBar(Context c,Object attrs,int style){}
 public void setIndeterminate(boolean b){}
 public void setMax(int v){}public void setProgress(int v){}
}""",
'android/util/Log.java': """package android.util; public class Log {
 public static int w(String tag,String msg,Throwable ex){return 0;}
}""",
'android/provider/DocumentsContract.java': """package android.provider;
import android.content.ContentResolver;import android.net.Uri;import java.io.FileNotFoundException;
public class DocumentsContract {
 public static String getTreeDocumentId(Uri u){return "";}
 public static String getDocumentId(Uri u){return "";}
 public static Uri buildDocumentUriUsingTree(Uri u,String id){return null;}
 public static Uri buildChildDocumentsUriUsingTree(Uri u,String id){return null;}
 public static Uri createDocument(ContentResolver r,Uri p,String mime,String name)throws FileNotFoundException{return null;}
 public static Uri renameDocument(ContentResolver r,Uri p,String name)throws FileNotFoundException{return null;}
 public static boolean deleteDocument(ContentResolver r,Uri p)throws FileNotFoundException{return false;}
 public static final class Document {
  public static final String COLUMN_MIME_TYPE="",COLUMN_FLAGS="",COLUMN_DISPLAY_NAME="",MIME_TYPE_DIR="";
  public static final int FLAG_DIR_SUPPORTS_CREATE=8;
 }
}"""
})


# M0.10: durable recovery journal uses SharedPreferences.
stubs.update({
'android/content/SharedPreferences.java': """package android.content;
public interface SharedPreferences {
 String getString(String k,String fallback);
 Editor edit();
 interface Editor {
  Editor putString(String k,String v);
  Editor clear();
  boolean commit();
 }
}"""
})
stubs['android/content/Context.java'] = stubs['android/content/Context.java'].replace(
 'public class Context {', 'public class Context { public static final int MODE_PRIVATE=0;'
).replace(
 'public String getPackageName()',
 'public SharedPreferences getSharedPreferences(String name,int mode){return null;}public String getPackageName()'
)

with tempfile.TemporaryDirectory() as tmp:
 t=Path(tmp)
 for n,content in stubs.items():
  p=t/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 app=list((root/'app/src/main/java').rglob('*.java'))
 cmd=['javac','--release','17','-encoding','UTF-8','-d',str(t/'classes'),*map(str,t.rglob('*.java')),*map(str,app)]
 r=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
 print('Java Android-signature stub smoke:', 'PASS' if r.returncode==0 else 'FAIL')
 print('App Java files:',len(app),'Stub classes:',len(stubs))
 print(r.stdout[:9000])
 raise SystemExit(r.returncode)
