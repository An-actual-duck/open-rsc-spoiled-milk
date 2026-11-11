package orsc;
public final class BrowserLoadProfileFixture {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("load profile check "+checks);}
    public static void main(String[] args){
        BrowserLoadProfile p=new BrowserLoadProfile(3,7);
        p.values[4]=960;p.values[5]=540;
        p.enter(BrowserLoadProfile.GEOMETRY);
        p.progress(new int[]{1,2,4,8,3,2,0,0,1,960,540,0});
        p.submitted(false,123,1456);p.submitted(true,34,456);
        p.values[BrowserLoadProfile.PATH]=2;
        double[] a=p.finish(10,12345);
        check(a.length==36&&a[0]==1&&a[1]==3&&a[2]==7&&a[3]==2);
        check(a[6]==4&&a[7]==8&&a[8]==3&&a[9]==2&&a[10]==5&&a[34]==5);
        check(a[23]==1&&a[24]==1&&a[25]==157&&a[26]==1912&&a[29]==10&&a[30]==12345&&a[31]==2);
        for(double x:a)check(Double.isFinite(x)&&x>=0);
        a[1]=99;check(p.values[1]==3);
        check(p.report(4,2));
        BrowserLoadProfile steady=new BrowserLoadProfile(3,8);steady.values[3]=3;steady.values[6]=4;
        check(!steady.report(4,3));check(steady.report(3,3));
        steady.values[3]=4;check(steady.report(4,3));check(!steady.report(4,4));
        steady.values[3]=0;check(steady.report(4,0));
        System.out.println("PASS load profile scalar snapshot "+checks+" checks; no browser/performance proof");
    }
}
