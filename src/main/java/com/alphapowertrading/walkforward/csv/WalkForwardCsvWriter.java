package com.alphapowertrading.walkforward.csv;
import com.alphapowertrading.walkforward.model.WalkForwardResult;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WalkForwardCsvWriter {
 public void write(String file,List<WalkForwardResult> results,String sort){Path p=Path.of(file);try{if(p.getParent()!=null)Files.createDirectories(p.getParent());List<WalkForwardResult> s=results.stream().sorted(comparator(sort)).toList();try(BufferedWriter w=Files.newBufferedWriter(p)){w.write("WINDOW,IS_START,IS_END,OOS_START,OOS_END,TP,TPH,SL,IS_CAGR,IS_SHARPE,IS_MAX_DD,OOS_CAGR,OOS_SHARPE,OOS_MAX_DD,OOS_FINAL_EQUITY,OOS_TRADES,OOS_WIN_RATE,OOS_TO_IS_CAGR\n");for(WalkForwardResult r:s){w.write(String.format(Locale.US,"%d,%s,%s,%s,%s,%.6f,%.6f,%.6f,%.8f,%.6f,%.8f,%.8f,%.6f,%.8f,%.2f,%d,%.4f,%.6f%n",r.window(),r.isStart(),r.isEnd(),r.oosStart(),r.oosEnd(),r.tp(),r.tph(),r.sl(),r.isCagr(),r.isSharpe(),r.isMaxDrawdown(),r.oosCagr(),r.oosSharpe(),r.oosMaxDrawdown(),r.oosFinalEquity(),r.oosTrades(),r.oosWinRate(),r.oosToIsCagr()));}}}catch(IOException e){throw new IllegalStateException("Unable to write walk-forward CSV: "+p.toAbsolutePath(),e);}}
 private Comparator<WalkForwardResult> comparator(String s){if(s==null)return Comparator.comparingInt(WalkForwardResult::window);return switch(s.toLowerCase(Locale.ROOT)){case "oos-cagr"->Comparator.comparingDouble(WalkForwardResult::oosCagr).reversed();case "oos-sharpe"->Comparator.comparingDouble(WalkForwardResult::oosSharpe).reversed();case "oos-maxdd"->Comparator.comparingDouble(WalkForwardResult::oosMaxDrawdown).reversed();case "oos-to-is-cagr"->Comparator.comparingDouble(WalkForwardResult::oosToIsCagr).reversed();default->Comparator.comparingInt(WalkForwardResult::window);};}
}
