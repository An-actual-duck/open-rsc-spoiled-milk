import com.sun.source.tree.*;
import com.sun.source.util.*;
import javax.tools.*;
import java.nio.file.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Parse only: no application class loading/resolution or gameplay execution. */
public final class SourceAccessInventory {
    static String quote(String text) {return "\""+text.replace("\\","\\\\").replace("\"","\\\"").replace("\r","\\r").replace("\n","\\n")+"\"";}
    static String compact(Tree tree){return tree.toString().trim();}
    static final Set<String> aliases=new HashSet<String>(Arrays.asList("pixelData","ownerPixels","transmittance","directFramebufferPixels","directFramebufferImage","game_image"));
    static final Set<String> calls=new HashSet<String>(Arrays.asList("shadeScanline","copyBlock16","copyBlock4","copyColorTo","createDirectFramebufferImage","borrowOverlay","copyPixelsToRgba","copyPackedIntRgbToRgba","getDataBuffer","getData","setPixels","setGameImage"));
    public static void main(String[] args)throws Exception {
        Path root=Paths.get(args[0]).toRealPath();List<Path> paths=new ArrayList<Path>();
        for(String dir:new String[]{"Client_Base/src","PC_Client/src"})try(java.util.stream.Stream<Path> files=Files.walk(root.resolve(dir))){files.filter(p->p.toString().endsWith(".java")).forEach(paths::add);}
        Collections.sort(paths);JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        try(StandardJavaFileManager manager=compiler.getStandardFileManager(null,null,null)){
            JavacTask task=(JavacTask)compiler.getTask(null,manager,d->{if(d.getKind()==Diagnostic.Kind.ERROR)throw new IllegalStateException(d.toString());},Arrays.asList("-proc:none"),null,manager.getJavaFileObjectsFromPaths(paths));
            for(CompilationUnitTree unit:task.parse())new TreePathScanner<Void,Void>() {
                String rawHash(Tree node){try{SourcePositions positions=Trees.instance(task).getSourcePositions();long start=positions.getStartPosition(unit,node),end=positions.getEndPosition(unit,node);String raw=unit.getSourceFile().getCharContent(true).subSequence((int)start,(int)end).toString();byte[] hash=MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte v:hash)b.append(String.format("%02x",v&255));return b.toString();}catch(Exception error){throw new IllegalStateException(error);}}
                String method(){TreePath p=getCurrentPath();while(p!=null){if(p.getLeaf() instanceof MethodTree){MethodTree m=(MethodTree)p.getLeaf();StringBuilder b=new StringBuilder(m.getName());b.append('(');for(VariableTree v:m.getParameters())b.append(compact(v.getType())).append(',');return b.append(')').toString();}p=p.getParentPath();}return "<field>";}
                void record(String kind,Tree node){
                    TreePath p=getCurrentPath();Tree expr=node;while(p.getParentPath()!=null&&p.getParentPath().getLeaf() instanceof ExpressionTree){p=p.getParentPath();expr=p.getLeaf();}
                    String file=root.relativize(Paths.get(unit.getSourceFile().toUri())).toString();
                    System.out.println("{"+"\"file\":"+quote(file)+",\"method\":"+quote(method())+",\"kind\":"+quote(kind)+",\"expression\":"+quote(compact(expr))+(kind.equals("method-body")?",\"rawSha256\":"+quote(rawHash(node)):"")+"}");
                }
                @Override public Void visitIdentifier(IdentifierTree n,Void v){if(aliases.contains(n.getName().toString()))record(n.getName().toString(),n);return super.visitIdentifier(n,v);}
                @Override public Void visitMemberSelect(MemberSelectTree n,Void v){if(aliases.contains(n.getIdentifier().toString()))record(n.getIdentifier().toString(),n);return super.visitMemberSelect(n,v);}
                @Override public Void visitMethod(MethodTree n,Void v){if(n.getBody()!=null)record("method-body",n.getBody());return super.visitMethod(n,v);}
                @Override public Void visitMethodInvocation(MethodInvocationTree n,Void v){String name=n.getMethodSelect().toString();int dot=name.lastIndexOf('.');name=name.substring(dot+1);if(calls.contains(name)||name.equals("flushPendingSegmentedText"))record("call:"+name,n);return super.visitMethodInvocation(n,v);}
            }.scan(unit,null);
        }
    }
}
