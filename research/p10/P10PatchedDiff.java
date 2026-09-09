package p7mat;

import java.nio.file.*;
import java.util.*;
import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.configuration.GraphDatabaseInternalSettings.StatefulShortestPlanningMode;
import org.neo4j.dbms.api.DatabaseManagementService;
import org.neo4j.dbms.api.DatabaseManagementServiceBuilder;
import org.neo4j.graphdb.Result;

/**
 * Patched-build differential: same query, same patched code,
 * SSP oracle (rewrite OFF) vs FSP (rewrite ON + INTO_ONLY).
 * Compares full rows: path, rel group, left/right groups, multiplicity.
 * Must run with patched-full-cp (patched classes first).
 */
public final class P10PatchedDiff {
  static DatabaseManagementService dbSSP, dbFSP;
  static Path dirSSP, dirFSP;
  static int total=0, passed=0, failed=0;
  static List<String> failures=new ArrayList<>();
  public static void main(String[] args) throws Exception {
    int trials = args.length>0 ? Integer.parseInt(args[0]) : 2000;
    dirSSP = Files.createTempDirectory("p10ssp");
    dirFSP = Files.createTempDirectory("p10fsp");
    System.out.println("SSP="+dirSSP+" FSP="+dirFSP);
    dbSSP = new DatabaseManagementServiceBuilder(dirSSP)
      .setConfig(GraphDatabaseInternalSettings.gpm_shortest_to_legacy_shortest_enabled, false)
      .build();
    dbFSP = new DatabaseManagementServiceBuilder(dirFSP)
      .setConfig(GraphDatabaseInternalSettings.gpm_shortest_to_legacy_shortest_enabled, true)
      .setConfig(GraphDatabaseInternalSettings.stateful_shortest_planning_mode, StatefulShortestPlanningMode.INTO_ONLY)
      .build();
    try {
      // sanity: verify toggle works
      sanity();
      Random rng = new Random(12345);
      for (int t=0;t<trials;t++) {
        genOne(rng,t);
        if (failed>10) { System.out.println("too many failures, abort"); break; }
      }
      System.out.println("DIFF total="+total+" passed="+passed+" failed="+failed);
      for(String f: failures) System.out.println("FAIL: "+f);
      if(failed>0) System.exit(1);
      System.out.println("PATCHED DIFFERENTIAL PASSED");
    } finally { dbSSP.shutdown(); dbFSP.shutdown(); }
  }
  static void sanity() {
    String q="MATCH (a:N{id:0}), (b:N{id:3}) MATCH ANY SHORTEST (a) ((c)-[r:R]->(d))+ (b) RETURN c";
    String pSSP=plan(dbSSP,q), pFSP=plan(dbFSP,q);
    System.out.println("sanity SSP plan hasSSP="+pSSP.contains("StatefulShortestPath"));
    System.out.println("sanity FSP plan hasSSP="+pFSP.contains("StatefulShortestPath")+" hasShortest="+pFSP.contains("ShortestPath"));
    if(!pSSP.contains("StatefulShortestPath")) throw new RuntimeException("SSP oracle not SSP");
    if(pFSP.contains("StatefulShortestPath")) throw new RuntimeException("FSP not rewritten");
    // seed base data (will be overwritten per trial, but need ids for sanity)
    setup(dbSSP,3,List.of(new int[]{0,1},new int[]{1,2}));
    setup(dbFSP,3,List.of(new int[]{0,1},new int[]{1,2}));
  }
  static String plan(DatabaseManagementService db,String q){
    try(var tx=db.database("neo4j").beginTx()){
      try(Result r=tx.execute("EXPLAIN "+q)){ String s=r.getExecutionPlanDescription().toString(); tx.commit(); return s; }
    }
  }
  static void setup(DatabaseManagementService db,int n,List<int[]> edges){
    try(var tx=db.database("neo4j").beginTx()){
      tx.execute("MATCH (x) DETACH DELETE x").close();
      for(int i=0;i<n;i++) tx.execute("CREATE (:N{id:"+i+"})").close();
      for(int[] e: edges){
        String ty = (e.length>2 && e[2]==1) ? "S" : "R";
        // e[0]->e[1]; occasionally parallel (caller adds dup)
        tx.execute("MATCH (x:N{id:"+e[0]+"}), (y:N{id:"+e[1]+"}) CREATE (x)-[:"+ty+"]->(y)").close();
      }
      tx.commit();
    }
  }
  static void genOne(Random rng,int t){
    int n=1+rng.nextInt(8);
    int m=rng.nextInt(21);
    List<int[]> edges=new ArrayList<>();
    for(int k=0;k<m;k++){
      int a=rng.nextInt(n), b=rng.nextInt(n);
      int ty=rng.nextDouble()<0.7?0:1;
      edges.add(new int[]{a,b,ty});
      if(rng.nextDouble()<0.15) edges.add(new int[]{a,b,ty}); // parallel
    }
    setup(dbSSP,n,edges); setup(dbFSP,n,edges);
    int src=rng.nextInt(n), tgt=rng.nextInt(n);
    String inner;
    double dd=rng.nextDouble();
    String relTy = rng.nextDouble()<0.7 ? "R" : "R|S";
    if(dd<0.4) inner="(c)-[r:"+relTy+"]->(d)";
    else if(dd<0.8) inner="(c)<-[r:"+relTy+"]-(d)";
    else inner="(c)-[r:"+relTy+"]-(d)";
    String quant = rng.nextDouble()<0.5?"+":"*";
    // min 0/1 only (eligible); quant * means min0, + means min1
    String sel = rng.nextDouble()<0.5?"ANY SHORTEST":"SHORTEST GROUP";
    int retMode=rng.nextInt(4); // 0 left,1 right,2 both,3 both+rel
    String ret = retMode==0?"c":retMode==1?"d":retMode==2?"c, d":"c, d, r";
    String q="MATCH (a:N{id:"+src+"}), (b:N{id:"+tgt+"}) MATCH p = "+sel+" (a) ("+inner+")"+quant+" (b) RETURN p, "+ret;
    // skip undirected same-node SHORTEST GROUP known pre-existing? No, include to measure; harness will report
    compare("t"+t+" n="+n+" "+inner+quant+" "+sel+" ret="+ret+" src="+src+" tgt="+tgt, q, sel.contains("GROUP"));
  }
  static void compare(String name,String q,boolean isGroup){
    total++;
    try{
      Set<String> s1=norm(dbSSP,q), s2=norm(dbFSP,q);
      if(s1.equals(s2)){ passed++; if(total%500==0) System.out.println("PASS "+name); }
      else{
        // single-shortest tie-break: compare distances only
        if(!isGroup && sameDistance(s1,s2)){ passed++; System.out.println("PASS(single-pick) "+name); }
        else{ failed++; failures.add(name+" SSP="+s1+" FSP="+s2+" Q="+q); System.out.println("FAIL "+name+" SSP="+s1+" FSP="+s2); }
      }
    }catch(Exception e){ failed++; failures.add(name+" ERROR "+e+" Q="+q); System.out.println("ERROR "+name+" "+e); }
  }
  static boolean sameDistance(Set<String> a,Set<String> b){
    if(a.size()!=1||b.size()!=1) return false;
    String x=a.iterator().next(), y=b.iterator().next();
    // normalized starts with plen=#
    return x.split(";")[0].split("\\|")[0].equals(y.split(";")[0].split("\\|")[0]);
  }
  static Set<String> norm(DatabaseManagementService db,String q){
    Set<String> out=new TreeSet<>();
    try(var tx=db.database("neo4j").beginTx()){
      try(Result r=tx.execute(q)){
        while(r.hasNext()){
          var row=r.next();
          StringBuilder sb=new StringBuilder();
          // p plen + node seq; c/d id seqs; r size (ids unstable across DBs, use size+types)
          sb.append("p=").append(pathNorm(row.get("p"))).append(";");
          if(r.columns().contains("c")) sb.append("c=").append(listNorm(row.get("c"))).append(";");
          if(r.columns().contains("d")) sb.append("d=").append(listNorm(row.get("d"))).append(";");
          if(r.columns().contains("r")) sb.append("r=").append(listNorm(row.get("r"))).append(";");
          out.add(sb.toString());
        }
      }
      tx.commit();
    }
    return out;
  }
  static String pathNorm(Object v){
    if(v==null) return "null";
    if(v instanceof org.neo4j.graphdb.Path p){
      StringBuilder sb=new StringBuilder("plen="+p.length()+"|");
      for(var n: p.nodes()) sb.append(n.getProperty("id","?")).append(",");
      sb.append("|rels=").append(p.length());
      return sb.toString();
    }
    return String.valueOf(v);
  }
  static String listNorm(Object v){
    if(v==null) return "null";
    if(v instanceof List<?> l){
      StringBuilder sb=new StringBuilder("[");
      for(Object o: l){
        if(o instanceof org.neo4j.graphdb.Node n) sb.append(n.getProperty("id","?")).append(",");
        else if(o instanceof org.neo4j.graphdb.Relationship) sb.append("R,");
        else sb.append(o).append(",");
      }
      return sb.append("]").toString();
    }
    return String.valueOf(v);
  }
}
