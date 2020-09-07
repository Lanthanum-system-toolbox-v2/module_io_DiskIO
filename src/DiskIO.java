import android.content.Context;
import android.content.DialogInterface;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import utils.LSpinner_helper;
import utils.NumAndBoolean;
import utils.StringToList;
import xzr.La.systemtoolbox.modules.java.LModule;
import xzr.La.systemtoolbox.ui.StandardCard;
import xzr.La.systemtoolbox.ui.views.LSpinner;
import xzr.La.systemtoolbox.utils.process.ShellUtil;

import java.util.ArrayList;
import java.util.List;

public class DiskIO implements LModule {

    public static String TAG="DiskIO";
    boolean is_emmc;
    boolean is_ufs;
    ArrayList<String> group;
    final String schedulers_node="scheduler";
    final String readahead_node="read_ahead_kb";
    final String rq_node="rq_affinity";
    final String rotational_node="rotational";
    final String add_random_node="add_random";
    final String iostats_node="iostats";
    final String nr_requests_node="nr_requests";
    final String nomerges_node="nomerges";

    @Override
    public String classname() {
        return "io";
    }

    @Override
    public View init(Context context) {
        disk_type_check();
        if(!is_emmc&&!is_ufs)
            return null;

        LinearLayout linearLayout=new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        TextView title= StandardCard.title(context);
        title.setText("存储IO配置");
        linearLayout.addView(title);
        TextView subtitle=StandardCard.subtitle(context);
        subtitle.setText("您可以在此配置存储设备的IO选项");
        linearLayout.addView(subtitle);

        ArrayList<String> emmc=new ArrayList<>();
        ArrayList<String> ufs=new ArrayList<>();
        group=new ArrayList<>();

        if(is_emmc) {
            String ret = ShellUtil.run("cd /sys/block\n" +
                    "for i in mmcblk*\n" +
                    "do\n" +
                    "if [ -d $i ]\n" +
                    "then\n" +
                    "echo $i\n" +
                    "fi\n" +
                    "done\n", true);
            emmc = StringToList.to(ret);
        }

        if(is_ufs) {
            String ret = ShellUtil.run("cd /sys/block\n" +
                    "for i in sd*\n" +
                    "do\n" +
                    "if [ -d $i ]\n" +
                    "then\n" +
                    "echo $i\n" +
                    "fi\n" +
                    "done\n", true);
            ufs = StringToList.to(ret);
        }

        group.addAll(emmc);
        group.addAll(ufs);

        for(String groupname:group){
            Button button=new Button(context);
            button.setBackgroundColor(android.R.attr.buttonBarButtonStyle);
            button.setText(translate(groupname));
            linearLayout.addView(button);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showdialog(context,groupname);
                }
            });
        }
        return linearLayout;
    }
    String cat(String path){
        return "cat "+path;
    }

    String write(String path,String value){
        return "echo \""+value+"\" > "+path;
    }

    String gen_queue_path(String groupname){
        return "/sys/block/"+groupname+"/queue/";
    }

    void showdialog(Context context,String groupname){
        final String queue_path=gen_queue_path(groupname);
        ScrollView scrollView=new ScrollView(context);
        LinearLayout linearLayout=new LinearLayout(context);
        scrollView.addView(linearLayout);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        {
            TextView textView = StandardCard.subtitle(context);
            textView.setText("调度器：");
            linearLayout.addView(textView);
        }
        String schedulers=ShellUtil.run(cat(queue_path+schedulers_node),true);
        List<String> iogovs= StringToList.iogov(schedulers);
        LSpinner gov=new LSpinner(context,iogovs);
        linearLayout.addView(gov);
        gov.setSelection(LSpinner_helper.label2position(iogovs,StringToList.getCurrentIOGov(schedulers)));
        {
            TextView textView = new TextView(context);
            textView.setText("* 根据大量测试的结果，cfq是综合性能最强的调度器，其性能甚至好于第三方魔改调度器（anxiety、maple、fiops等）");
            linearLayout.addView(textView);
        }
        {
            TextView textView = StandardCard.subtitle(context);
            textView.setText("预读(kB)：");
            linearLayout.addView(textView);
        }
        EditText readahead=new EditText(context);
        linearLayout.addView(readahead);
        readahead.setText(ShellUtil.run(cat(queue_path+readahead_node),true));
        {
            TextView textView = new TextView(context);
            textView.setText("* 在高性能闪存上，建议的预读是128kB，过大的预读不会提升性能，反而造成大量无用额外开销。（预读是设计给机械硬盘用的）");
            linearLayout.addView(textView);
        }
        {
            TextView textView = StandardCard.subtitle(context);
            textView.setText("RQ归属：");
            linearLayout.addView(textView);
        }
        {
            TextView textView = new TextView(context);
            textView.setText("配置提交IO请求的CPU与完成软&硬中断的CPU之间的关系。这个过程可能牵扯到三个CPU：发起请求的CPU、完成硬中断的CPU、完成软中断的CPU。");
            linearLayout.addView(textView);
        }
        ArrayList<String> rq_list=new ArrayList(){{
            add("无任何关系");
            add("要求上述CPU中的两个共享缓存");
            add("要求上述CPU中的两个为同一个核心");
        }};
        LSpinner rq=new LSpinner(context,rq_list);
        linearLayout.addView(rq);
        try {
            rq.setSelection(Integer.parseInt(ShellUtil.run(cat(queue_path+rq_node),true)));
        }catch (Exception e){

        }

        {
            TextView textView = new TextView(context);
            textView.setText("* 三个CPU之间联系越密切意味着对缓存中的数据利用效率越高，但是并没有人说同一个核心是最好的。。。");
            linearLayout.addView(textView);
        }

        Switch rotational=new Switch(context);
        linearLayout.addView(rotational);
        rotational.setText("标注为机械硬盘");
        rotational.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run(cat(queue_path+rotational_node),true)));

        Switch add_random=new Switch(context);
        add_random.setText("启用熵贡献");
        linearLayout.addView(add_random);
        add_random.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run(cat(queue_path+add_random_node),true)));
        {
            TextView textView = new TextView(context);
            textView.setText("* 能贡献熵的前提是硬件具有随机性（机械硬盘），所以，它对于手机没有卵用。");
            linearLayout.addView(textView);
        }

        Switch iostats=new Switch(context);
        iostats.setText("IO统计");
        linearLayout.addView(iostats);
        iostats.setChecked(NumAndBoolean.Num2Boolean(ShellUtil.run(cat(queue_path+iostats_node),true)));
        {
            TextView textView = new TextView(context);
            textView.setText("* 关闭这个选项有助于降低访问延时，但是你将无法监控存储器的IO状态。");
            linearLayout.addView(textView);
        }

        {
            TextView textView = StandardCard.subtitle(context);
            textView.setText("允许的最大请求数量：");
            linearLayout.addView(textView);
        }
        EditText nr_requests=new EditText(context);
        linearLayout.addView(nr_requests);
        nr_requests.setText(ShellUtil.run(cat(queue_path+nr_requests_node),true));
        {
            TextView textView = new TextView(context);
            textView.setText("* 这代表读取或写入队列或每个CGroup请求池的允许最大请求数量");
            linearLayout.addView(textView);
        }
        {
            TextView textView = StandardCard.subtitle(context);
            textView.setText("IO合并：");
            linearLayout.addView(textView);
        }
        ArrayList<String> nomerges_list=new ArrayList(){{
            add("允许所有合并");
            add("仅允许单次命中合并");
            add("禁用所有合并");
        }};
        LSpinner nomerges=new LSpinner(context,nomerges_list);
        linearLayout.addView(nomerges);
        try {
            nomerges.setSelection(Integer.parseInt(ShellUtil.run(cat(queue_path + nomerges_node), true)));
        }
        catch (Exception e){
        }
        {
            TextView textView = new TextView(context);
            textView.setText("* 禁用、削减合并可能影响IO性能");
            linearLayout.addView(textView);
        }

        new AlertDialog.Builder(context)
                .setTitle("编辑“"+translate(groupname)+"”的存储IO配置")
                .setNegativeButton("取消",null)
                .setView(scrollView)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ShellUtil.run(write(queue_path+schedulers_node,gov.getSelectedLabel()),true);
                        ShellUtil.run(write(queue_path+readahead_node,readahead.getText().toString()),true);
                        ShellUtil.run(write(queue_path+rq_node,rq.getSelection()+""),true);
                        ShellUtil.run(write(queue_path+rotational_node,NumAndBoolean.Boolean2Num(rotational.isChecked())+""),true);
                        ShellUtil.run(write(queue_path+add_random_node,NumAndBoolean.Boolean2Num(add_random.isChecked())+""),true);
                        ShellUtil.run(write(queue_path+iostats_node,NumAndBoolean.Boolean2Num(iostats.isChecked())+""),true);
                        ShellUtil.run(write(queue_path+nr_requests_node,nr_requests.getText().toString()),true);
                        ShellUtil.run(write(queue_path+nomerges_node,nomerges.getSelection()+""),true);
                        }
                }).create().show();
    }

    void disk_type_check(){
        if(ShellUtil.run("if [ -d /sys/block/mmcblk0 ]\nthen\necho true\nfi\n",true).equals("true"))
            is_emmc=true;
        if(ShellUtil.run("if [ -d /sys/block/sda ]\nthen\necho true\nfi\n",true).equals("true"))
            is_ufs=true;
    }

    String translate(String src){
        if(is_emmc)
            src=src.replace("mmcblk","EMMC设备");
        if(is_ufs)
            src=src.replace("sd","UFS设备");
        return src;
    }

    @Override
    public String onBootApply() {
        String cmd="";
        for (String groupname:group){
            cmd+=write(gen_queue_path(groupname)+schedulers_node,ShellUtil.run(cat(gen_queue_path(groupname)+schedulers_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+readahead_node,ShellUtil.run(cat(gen_queue_path(groupname)+readahead_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+rq_node,ShellUtil.run(cat(gen_queue_path(groupname)+rq_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+rotational_node,ShellUtil.run(cat(gen_queue_path(groupname)+rotational_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+add_random_node,ShellUtil.run(cat(gen_queue_path(groupname)+add_random_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+iostats_node,ShellUtil.run(cat(gen_queue_path(groupname)+iostats_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+nr_requests_node,ShellUtil.run(cat(gen_queue_path(groupname)+nr_requests_node),true))+"\n";
            cmd+=write(gen_queue_path(groupname)+nomerges_node,ShellUtil.run(cat(gen_queue_path(groupname)+nomerges_node),true))+"\n";
        }
        return cmd;
    }

    @Override
    public void onExit() {

    }
}
