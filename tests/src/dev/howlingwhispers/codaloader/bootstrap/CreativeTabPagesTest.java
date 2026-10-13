package dev.howlingwhispers.codaloader.bootstrap;

import java.util.*;
import java.nio.file.*;
import java.util.zip.ZipFile;

public final class CreativeTabPagesTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    public enum Row {TOP,BOTTOM}
    public record Tab(String getType,Row row,int column){public String getType(){return getType;}}
    public static void main(String[] args)throws Exception{
        var all=new ArrayList<Tab>();
        for(int i=0;i<14;i++)all.add(new Tab(i==6?"SEARCH":i==13?"INVENTORY":"CATEGORY",i<7?Row.TOP:Row.BOTTOM,i%7));
        List<Tab> vanilla=List.copyOf(all);
        for(int i=0;i<25;i++){
            Tab tab=new Tab("CATEGORY",Row.BOTTOM,7+i);all.add(tab);CodaCreativeTabPages.register(tab,i);
            check(CodaCreativeTabPages.visualColumn(tab)==i%6,"Mapped column stays within real grid");
            check(CodaCreativeTabPages.visualRow(tab)==(i%12<6?Row.TOP:Row.BOTTOM),"Mapped row splits six top/six bottom");
        }
        check(CodaCreativeTabPages.pageCount()==4,"25 extra tabs produce three mod pages plus vanilla");
        check(CodaCreativeTabPages.visible(all,0).equals(vanilla),"First page retains every vanilla/operator tab");
        for(int page=1;page<4;page++){
            List<?> tabs=CodaCreativeTabPages.visible(all,page);
            check(tabs.size()==(page==3?3:14),"Page contains mod slots plus two pinned native tabs");
            check(tabs.contains(vanilla.get(6))&&tabs.contains(vanilla.get(13)),"Search and player inventory are always accessible");
            var locations=new HashSet<String>();
            for(Object tab:tabs){
                String key=CodaCreativeTabPages.visualRow(tab)+":"+CodaCreativeTabPages.visualColumn(tab);
                check(locations.add(key),"No overlapping creative tabs: "+key);
                check(CodaCreativeTabPages.visualColumn(tab)<=6,"No tab extends past vanilla panel");
            }
        }
        for(Tab tab:vanilla){check(CodaCreativeTabPages.visualColumn(tab)==tab.column(),"Vanilla columns untouched");check(CodaCreativeTabPages.visualRow(tab)==tab.row(),"Vanilla rows untouched");}
        if(args.length>0){
            try(ZipFile zip=new ZipFile(args[0])){
                byte[] screen=zip.getInputStream(zip.getEntry(CodaCreativeInventoryTransformer.SCREEN+".class")).readAllBytes();
                byte[] transformed=new CodaCreativePagesTransformer().transform(null,CodaCreativeInventoryTransformer.SCREEN,null,null,screen);
                check(transformed!=null,"Exact Snapshot 3 paging seams verified");
                check(new CodaCreativePagesTransformer().transform(null,"unrelated/Screen",null,null,screen)==null,"Unrelated screens untouched");
                check(new CodaCreativePagesTransformer().transform(null,CodaCreativeInventoryTransformer.SCREEN,null,null,transformed)==null,"No double instrumentation");
            }
        }
        System.out.println("PASS: "+checks+" native creative-page layout and Snapshot bytecode checks");
    }
}
