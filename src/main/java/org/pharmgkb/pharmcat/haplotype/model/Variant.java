
package org.pharmgkb.pharmcat.haplotype.model;

import java.util.Comparator;
import com.google.common.base.Joiner;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.pharmgkb.pharmcat.definition.model.VariantLocus;
import org.pharmgkb.pharmcat.haplotype.SampleAllele;


public class Variant implements Comparable<Variant>  {
  private static final String sf_rsidFormat = "%s%s";
  private static final String sf_positionFormat = "%d%s";
  private static final Joiner sf_vcfAlleleJoiner = Joiner.on(",");

  @Expose
  @SerializedName("position")
  private long m_position;
  @Expose
  @SerializedName("rsid")
  private String m_rsid;
  @Expose
  @SerializedName("vcfCall")
  private String m_vcfCall;
  private boolean m_isPhased;
  private String m_vcfAlleles;


  /**
   * Primary constructor.
   */
  public Variant(VariantLocus variant, SampleAllele allele) {
    String call;
    String vcfAlleles = sf_vcfAlleleJoiner.join(allele.getVcfAlleles());
    if (allele.isPhased()) {
      call = allele.getAllele1() + "|" + allele.getAllele2();
    } else {
      call = allele.getAllele1() + "/" + allele.getAllele2();
    }
    initialize(variant.getPosition(), variant.getRsid(), call, vcfAlleles);
  }

  /**
   * Constructor for creating faux-{@link Variant}s for extra positions and tests.
   */
  public Variant(long pos, @Nullable String rsids, @Nullable String call, @Nullable String vcfAlleles) {
    initialize(pos, rsids, call, vcfAlleles);
  }

  private void initialize(long pos, @Nullable String rsids, @Nullable String call, @Nullable String vcfAlleles) {
    m_position = pos;
    m_rsid = rsids;
    m_vcfCall = call;
    if (call != null) {
      m_isPhased = call.contains("|");
    }
    m_vcfAlleles = vcfAlleles;
  }


  public long getPosition() {
    return m_position;
  }

  public @Nullable String getRsid() {
    return m_rsid;
  }

  public @Nullable String getVcfCall() {
    return m_vcfCall;
  }

  public boolean isPhased() {
    return m_isPhased;
  }

  public @Nullable String getVcfAlleles() {
    return m_vcfAlleles;
  }

  @Override
  public int compareTo(Variant o) {

    int rez = Long.compare(m_position, o.getPosition());
    if (rez != 0) {
      return rez;
    }
    return Comparator.nullsLast(String::compareTo).compare(m_vcfCall, o.getVcfCall());
  }

  public String toString() {
    String vcfCall = m_vcfCall == null ? null : m_vcfCall.replaceAll("[|/]", "");
    if (m_rsid != null) {
      return String.format(sf_rsidFormat, getRsid(), vcfCall);
    } else {
      return String.format(sf_positionFormat, getPosition(), vcfCall);
    }
  }
}
