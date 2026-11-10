package orsc.browser;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** One-time private receipt; missing experiments stay explicitly unavailable. */
final class BenchmarkCandidatePolicies {
    static boolean[] inspect(String owner,String method) {
        try {
            Method getter=Class.forName(owner).getMethod(method);
            Object value=getter.invoke(null);
            if(!(value instanceof Boolean))throw new IllegalStateException("Candidate getter does not return boolean: "+method);
            return new boolean[]{true,((Boolean)value).booleanValue()};
        } catch(ClassNotFoundException error){return new boolean[]{false,false};}
        catch(NoSuchMethodException error){return new boolean[]{false,false};}
        catch(IllegalAccessException error){throw new IllegalStateException("Candidate getter inaccessible: "+method,error);}
        catch(InvocationTargetException error){throw new IllegalStateException("Candidate getter failed: "+method,error.getCause());}
    }
    static final class RegionDiagnostics {
        private Class<?> owner;
        private Method getter;
        double[] snapshot(Object world) {
            if(world==null)return null;
            if(owner!=world.getClass()) {
                owner=world.getClass();
                try {getter=owner.getMethod("getBrowserRegionPreparationDiagnostics");}
                catch(NoSuchMethodException error){getter=null;}
            }
            if(getter==null)return null;
            try {
                Object raw=getter.invoke(world);
                if(!(raw instanceof long[])||((long[])raw).length!=3)throw new IllegalStateException("Region diagnostics layout changed");
                long[] source=(long[])raw;double[] values=new double[3];
                for(int n=0;n<3;n++){if(source[n]<0||(double)source[n]>9007199254740991d)throw new IllegalStateException("Region diagnostics exceeds exact numeric range");values[n]=source[n];}
                return values;
            } catch(IllegalAccessException error){throw new IllegalStateException("Region diagnostics inaccessible",error);}
            catch(InvocationTargetException error){throw new IllegalStateException("Region diagnostics failed",error.getCause());}
        }
    }

}
