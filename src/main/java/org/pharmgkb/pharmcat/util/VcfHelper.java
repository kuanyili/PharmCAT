package org.pharmgkb.pharmcat.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.google.gson.Gson;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.pharmgkb.pharmcat.definition.model.DefinitionExemption;
import org.pharmgkb.pharmcat.definition.model.DefinitionFile;
import org.pharmgkb.pharmcat.definition.model.VariantLocus;
import org.pharmgkb.pharmcat.haplotype.DefinitionReader;


/**
 * @author Mark Woon
 */
public class VcfHelper implements AutoCloseable {
  private static final String sf_vcfUrl = "https://api.pharmgkb.org/v1/pharmcat/hgvs/%s/vcf";
  private static final String sf_extraPositionUrl = "https://api.pharmgkb.org/v1/pharmcat/extraPosition/%s";

  private final CloseableHttpClient m_httpclient;
  private final Gson m_gson = new Gson();


  public VcfHelper() {
    m_httpclient = HttpClients.createDefault();
  }


  @Override
  public void close() throws IOException {
    m_httpclient.close();
  }


  /**
   * Translate HGVS to VCF.
   */
  public VcfData hgvsToVcf(String hgvs) throws IOException {
    String url = String.format(sf_vcfUrl, URLEncoder.encode(hgvs, StandardCharsets.UTF_8.name()));
    return new VcfData(runQuery(url));
  }

  /**
   * Translate RSID to {@link VariantLocus}.
   */
  public VariantLocus rsidToVariantLocus(String rsid) throws IOException {
    String url = String.format(sf_extraPositionUrl, URLEncoder.encode(rsid, StandardCharsets.UTF_8.name()));
    VcfData vcfData = new VcfData(runQuery(url));

    VariantLocus vl = new VariantLocus(vcfData.chrom, vcfData.pos,
        vcfData.hgvs.substring(0, vcfData.hgvs.indexOf(":") + 1));
    vl.setRsid(rsid);
    vl.setRef(vcfData.ref);
    vl.addAlt(vcfData.alt);

    SortedSet<String> cpicAlleles = new TreeSet<>();
    cpicAlleles.add(vcfData.ref);
    cpicAlleles.add(vcfData.alt);
    vl.setCpicAlleles(cpicAlleles);

    Map<String, String> vcfMap = new HashMap<>();
    vcfMap.put(vcfData.ref, vcfData.ref);
    vcfMap.put(vcfData.alt, vcfData.alt);
    vl.setCpicToVcfAlleleMap(vcfMap);

    return vl;
  }

  private Map<String, Object> runQuery(String url) throws IOException {

    HttpGet httpGet = new HttpGet(url);
    httpGet.setHeader(HttpHeaders.ACCEPT, "application/json");
    try (CloseableHttpResponse response = m_httpclient.execute(httpGet)) {
      HttpEntity entity = response.getEntity();
      try (InputStreamReader reader = new InputStreamReader(entity.getContent())) {
        //noinspection unchecked
        return (Map<String, Object>)m_gson.fromJson(reader, Map.class).get("data");
      }
    }
  }



  /**
   * Runs bcftools to normalize VCF.
   */
  public static VcfData normalizeRepeats(String chr, Collection<VcfData> data) throws IOException {

    Path inFile = Files.createTempFile(null, ".vcf");
    Path outFile = Files.createTempFile(null, ".vcf");
    try {
      try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(inFile))) {
        writer.println("##fileformat=VCFv4.2");
        writer.println("##contig=<ID=" + chr + ",assembly=hg38,species=\"Homo sapiens\">");
        writer.println("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">");
        writer.println("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tsample");
        for (VcfData vcf : data) {
          printVcfLine(writer, chr, vcf.pos, null, vcf.ref, vcf.alt, null);
        }
      }

      DockerRunner.normalizeVcf(inFile, outFile);
      try (BufferedReader bufferedReader = Files.newBufferedReader(outFile)) {
        String line;
        do {
          line = bufferedReader.readLine();
        } while (line.startsWith("#"));
        List<VcfData> output = new ArrayList<>();
        while (line != null) {
          output.add(new VcfData(line));
          line = bufferedReader.readLine();
        }
        if (output.size() > 1) {
          throw new IllegalStateException("Could not merge repeats: " + output);
        }
        return output.get(0);
      }

    } finally {
      Files.deleteIfExists(inFile);
      Files.deleteIfExists(outFile);
    }
  }


  public static void extractPositions(Collection<String> genes, DefinitionReader definitionReader, Path file)
      throws IOException {

    try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(file))) {
      writer.println("##fileformat=VCFv4.2");
      writer.println("##source=PharmCAT allele definitions");
      writer.println("##fileDate=" + DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(ZonedDateTime.now()));
      HashMap<String, String> contigs = new HashMap<>();
      for (String gene : genes) {
        DefinitionFile definitionFile = definitionReader.getDefinitionFile(gene);
        contigs.put(definitionFile.getChromosome(), definitionFile.getGenomeBuild().replace("b", "hg"));
      }
      Set<String> assemblies = new HashSet<>(contigs.values());
      if (assemblies.size() > 1) {
        throw new IllegalStateException("Multiple assemblies found: " + assemblies);
      }
      String assembly = assemblies.iterator().next();
      for (String chr : contigs.keySet()) {
        writer.println("##contig=<ID=" + chr + ",assembly=" + assembly + ",species=\"Homo sapiens\">");
      }

      writer.println("##FILTER=<ID=PASS,Description=\"All filters passed\">");
      writer.println("##INFO=<ID=PX,Number=.,Type=String,Description=\"Gene\">");
      writer.println("##INFO=<ID=POI,Number=0,Type=Flag,Description=\"Position of Interest but not part of an allele definition\">");
      writer.println("##FORMAT=<ID=GT,Number=1,Type=String,Description=\"Genotype\">");
      writer.println("#CHROM\tPOS\tID\tREF\tALT\tQUAL\tFILTER\tINFO\tFORMAT\tPharmCAT");

      for (String gene : genes) {
        DefinitionFile definitionFile = definitionReader.getDefinitionFile(gene);
        for (VariantLocus vl : definitionFile.getVariants()) {
          printVcfLine(writer, definitionFile.getChromosome(), vl.getPosition(), vl.getRsid(), vl.getRef(),
              String.join(",", vl.getAlts()), "PX=" + gene);
        }

        DefinitionExemption exemption = definitionReader.getExemption(gene);
        if (exemption != null) {
          for (VariantLocus vl : exemption.getExtraPositions()) {
            printVcfLine(writer, definitionFile.getChromosome(), vl.getPosition(), vl.getRsid(), vl.getRef(),
                String.join(",", vl.getAlts()), "POI");
          }
        }
      }
    }
  }


  private static void printVcfLine(PrintWriter writer, String chr, long pos, String rsid, String ref, String alt,
      @Nullable String info) {

    writer.print(chr);
    writer.print("\t");
    writer.print(pos);
    writer.print("\t");
    if (rsid != null) {
      writer.print(rsid);
    } else {
      writer.print(".");
    }
    writer.print("\t");
    writer.print(ref);
    writer.print("\t");
    writer.print(alt);
    writer.print("\t." +   // qual
        "\tPASS\t");       // filter
    if (info != null) {
      writer.print(info);
    } else {
      writer.print(".");
    }
    writer.println("\tGT" +// format
        "\t0/0");          // sample
  }




  private static final Pattern sf_repeatPattern = Pattern.compile("^([ACGT]+)\\((\\d+)\\)$");

  public static String translateRepeat(String allele) {
    if (!allele.contains("[") && !allele.contains("]") && !allele.contains("(") && !allele.contains(")")) {
      return allele;
    }

    Matcher m = sf_repeatPattern.matcher(allele);
    if (m.matches()) {
      String repeatSeq = m.group(1);
      int numRepeats = Integer.parseInt(m.group(2));
      StringBuilder expandedAlelle = new StringBuilder();
      for (int x = 0; x < numRepeats; x += 1) {
        expandedAlelle.append(repeatSeq);
      }
      return expandedAlelle.toString();
    }
    throw new IllegalArgumentException("Unsupported repeat format: " + allele);
  }


  public static void main(String[] args) {

    try {
      VcfHelper vcfHelper = new VcfHelper();
      VcfData vcf = vcfHelper.hgvsToVcf("NC_000001.11:g.201060815C>T");
      System.out.println(vcf);

    } catch (Exception ex) {
      ex.printStackTrace();
    }
  }


  public static class VcfData {
    public String hgvs;
    public String chrom;
    public final long pos;
    public final String ref;
    public final String alt;

    VcfData(Map<String, Object> data) {
      hgvs = (String)data.get("hgvs");
      chrom = (String)data.get("chrom");
      pos = ((Number)data.get("position")).longValue();
      ref = (String)data.get("ref");
      alt = (String)data.get("alt");
    }

    /**
     * Parses data line from VCF.
     */
    VcfData(String vcfLine) {
      String[] data = vcfLine.split("\t");
      pos = Long.parseLong(data[1]);
      ref = data[3];
      alt = data[4];
    }

    VcfData(long pos, String ref, String alt) {
      this.pos = pos;
      this.ref = ref;
      this.alt = alt;
    }


    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof VcfData)) {
        return false;
      }
      VcfData o = (VcfData)obj;
      return Objects.equals(pos, o.pos) &&
          Objects.equals(ref, o.ref) &&
          Objects.equals(alt, o.alt);
    }

    @Override
    public String toString() {
      return "pos=" + pos + "; ref=" + ref + "; alt=" + alt;
    }
  }
}
