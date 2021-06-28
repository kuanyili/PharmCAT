package org.pharmgkb.pharmcat.definition.model;

import java.util.Objects;
import java.util.regex.Pattern;
import com.google.common.base.Preconditions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang3.StringUtils;
import org.pharmgkb.common.comparator.ChromosomeNameComparator;


/**
 * All the information used to describe a particular location of a variant.
 *
 * @author Ryan Whaley
 */
public class VariantLocus implements Comparable<VariantLocus> {
  public static final Pattern REPEAT_PATTERN = Pattern.compile("([ACGT]+)\\(([ACGT]+)\\)(\\d+)([ACGT]+)");
  @Expose
  @SerializedName("chromosome")
  private final String m_chromosome;
  @Expose
  @SerializedName("position")
  private final int m_position;
  @Expose
  @SerializedName("rsid")
  private String m_rsid;
  @Expose
  @SerializedName("chromosomeHgvsName")
  private final String m_chromosomeHgvsName;
  @Expose
  @SerializedName("geneHgvsName")
  private String m_geneHgvsName;
  @Expose
  @SerializedName("proteinNote")
  private String m_proteinNote;
  @Expose
  @SerializedName("resourceNote")
  private String m_resourceNote;
  @Expose
  @SerializedName("type")
  private VariantType m_type = VariantType.SNP;
  @Expose
  @SerializedName("referenceRepeat")
  private String m_referenceRepeat;


  public VariantLocus(String chromosome, int position, String chromosomeHgvsName) {
    Preconditions.checkNotNull(chromosome);
    Preconditions.checkNotNull(chromosomeHgvsName);
    m_chromosome = chromosome;
    m_position = position;
    m_chromosomeHgvsName = chromosomeHgvsName;
  }

  public String getChromosome() {
    return m_chromosome;
  }


  /**
   * Gets the chromosome and VCF position for this variant.
   */
  public String getVcfChrPosition() {
    return m_chromosome + ":" + getVcfPosition();
  }

  /**
   * Gets the VCF position for this variant.
   */
  public int getVcfPosition() {
    if (m_type == VariantType.DEL) {
      return m_position - 1;
    }
    return m_position;
  }


  /**
   * The (start) position on the chromosomal sequence.
   */
  public int getPosition() {
    return m_position;
  }


  /**
   * The name use for this location on the chromosomal sequence, should be relative to plus strand
   */
  public String getChromosomeHgvsName() {
    return m_chromosomeHgvsName;
  }


  /**
   * The name used for this location on the gene sequence, relative to the strand the gene is on
   */
  public String getGeneHgvsName() {
    return m_geneHgvsName;
  }

  public void setGeneHgvsName(String geneHgvsName) {
    m_geneHgvsName = geneHgvsName;
  }

  /**
   * The name use for this location on the protein sequence
   */
  public String getProteinNote() {
    return m_proteinNote;
  }

  public void setProteinNote(String proteinNote) {
    m_proteinNote = proteinNote;
  }

  /**
   * The identifier use for this location from dbSNP
   */
  public String getRsid() {
    return m_rsid;
  }

  public void setRsid(String rsid) {
    m_rsid = rsid;
  }


  /**
   * A common name for this variant, usually specified by some specialized resource
   */
  public String getResourceNote() {
    return m_resourceNote;
  }

  public void setResourceNote(String resourceNote) {
    m_resourceNote = resourceNote;
  }


  /**
   * Gets the type of variant.
   */
  public VariantType getType() {
    return m_type;
  }

  public void setType(VariantType type) {
    m_type = type;
  }


  /**
   * Gets the reference repeat, if this is a {@link VariantType#REPEAT}.
   */
  public String getReferenceRepeat() {
    return m_referenceRepeat;
  }

  public void setReferenceRepeat(String referenceRepeat) {
    Preconditions.checkArgument(REPEAT_PATTERN.matcher(referenceRepeat).matches());
    m_referenceRepeat = referenceRepeat;
  }


  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof VariantLocus)) {
      return false;
    }
    VariantLocus that = (VariantLocus)o;
    return m_position == that.getPosition() &&
        m_type == that.getType() &&
        Objects.equals(m_chromosomeHgvsName, that.getChromosomeHgvsName()) &&
        Objects.equals(m_geneHgvsName, that.getGeneHgvsName()) &&
        Objects.equals(m_proteinNote, that.getProteinNote()) &&
        Objects.equals(m_rsid, that.getRsid()) &&
        Objects.equals(m_resourceNote, that.getResourceNote());
  }

  @Override
  public int hashCode() {
    return Objects.hash(m_position, m_chromosomeHgvsName, m_geneHgvsName, m_proteinNote, m_rsid, m_resourceNote);
  }


  @Override
  public int compareTo(VariantLocus o) {

    int rez = ChromosomeNameComparator.getComparator().compare(m_chromosome, o.getChromosome());
    if (rez != 0) {
      return rez;
    }
    rez = Integer.compare(m_position, o.getPosition());
    if (rez != 0) {
      return rez;
    }
    return m_chromosomeHgvsName.compareTo(o.getChromosomeHgvsName());
  }

  @Override
  public String toString() {
    return String.format(
        "%s:%d%s",
        m_chromosome,
        m_position,
        StringUtils.isBlank(m_rsid) ? "" : String.format(" (%s)", m_rsid));
  }
}
