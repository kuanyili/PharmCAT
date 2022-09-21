package org.pharmgkb.pharmcat.reporter.model.result;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.stream.Collectors;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.pharmgkb.pharmcat.reporter.ReportContext;
import org.pharmgkb.pharmcat.reporter.model.DataSource;
import org.pharmgkb.pharmcat.reporter.model.MessageAnnotation;
import org.pharmgkb.pharmcat.reporter.model.cpic.Drug;
import org.pharmgkb.pharmcat.reporter.model.cpic.Publication;
import org.pharmgkb.pharmcat.reporter.model.pgkb.GuidelinePackage;


/**
 * This class is a wrapper around the {@link Drug} class that also handles the matching of genotype
 * functions to recommendations.
 *
 * @author Ryan Whaley
 */
public class DrugReport implements Comparable<DrugReport> {
  // NOTE: This is so the "No Recommendations" section doesn't show in the warfarin guideline
  private static final List<String> sf_notApplicableMatches = ImmutableList.of("RxNorm:11289"); // ID for warfarin

  @Expose
  @SerializedName("name")
  private final String f_name;
  @Expose
  @SerializedName("cpicId")
  private String m_cpicId;
  @Expose
  @SerializedName("pharmgkbId")
  private String m_pgkbId;
  @Expose
  @SerializedName("messages")
  private final List<MessageAnnotation> m_messages = new ArrayList<>();
  @Expose
  @SerializedName("variants")
  private final List<String> m_reportVariants = new ArrayList<>();
  @Expose
  @SerializedName("urls")
  private final List<String> f_urls = new ArrayList<>();
  @Expose
  @SerializedName("citations")
  private final List<Publication> f_citations = new ArrayList<>();
  @Expose
  @SerializedName("guidelines")
  private final List<GuidelineReport> f_guidelines = new ArrayList<>();


  public DrugReport(Drug drug, ReportContext reportContext) {
    f_name = drug.getDrugName();
    m_cpicId = drug.getDrugId();
    f_urls.add(drug.getUrl());
    if (drug.getCitations() != null) {
      // cpic data can have array with null value in it
      drug.getCitations().stream()
          .filter(Objects::nonNull)
          .forEach(f_citations::add);
    }

    // 1 guideline report per CPIC drug
    GuidelineReport guidelineReport = new GuidelineReport(drug);
    addGenes(guidelineReport, drug.getGenes(), reportContext, DataSource.CPIC);
    // add matching recommendations
    if (drug.getRecommendations() != null) {
      List<Genotype> possibleGenotypes = Genotype.makeGenotypes(guidelineReport.getRelatedGeneReports());
      guidelineReport.matchAnnotationsToGenotype(possibleGenotypes, drug);
    }
    f_guidelines.add(guidelineReport);
  }

  public DrugReport(String name, SortedSet<GuidelinePackage> guidelinePackages, ReportContext reportContext) {
    Preconditions.checkArgument(guidelinePackages != null && guidelinePackages.size() > 0);
    f_name = name;
    m_pgkbId = guidelinePackages.first().getGuideline().getRelatedChemicals().stream()
        .filter((c) -> c.getName().equals(name))
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("DPWG guideline " +
            guidelinePackages.first().getGuideline().getId() + " is supposd to be related to " + name + " but is not"))
        .getId();

    // DPWG drug can have multiple guideline reports
    for (GuidelinePackage guidelinePackage : guidelinePackages) {
      f_urls.add(guidelinePackage.getGuideline().getUrl());
      if (guidelinePackage.getCitations() != null) {
        f_citations.addAll(guidelinePackage.getCitations());
      }

      GuidelineReport guidelineReport = new GuidelineReport(guidelinePackage);
      addGenes(guidelineReport, guidelinePackage.getGenes(), reportContext, DataSource.DPWG);
      // add matching recommendations
      List<Genotype> possibleGenotypes = Genotype.makeGenotypes(guidelineReport.getRelatedGeneReports());
      guidelineReport.matchAnnotationsToGenotype(possibleGenotypes, guidelinePackage);
      f_guidelines.add(guidelineReport);
    }
  }

  private void addGenes(GuidelineReport guidelineReport, Collection<String> genes, ReportContext reportContext,
      DataSource source) {
    // link guideline report to gene report
    for (String geneSymbol : genes) {
      GeneReport geneReport = reportContext.getGeneReport(source, geneSymbol);
      guidelineReport.addRelatedGeneReport(geneReport);
      // add inverse relationship (gene report back to drug report)
      geneReport.addRelatedDrugs(this);
    }
  }


  /**
   * Gets the name of the drug this {@link DrugReport} is on.
   */
  public String getName() {
    return f_name;
  }

  /**
   * Gets the CPIC or the PGKB ID, whichever is specified, in that order
   * @return an ID for this drug
   */
  public String getId() {
    return MoreObjects.firstNonNull(m_cpicId, m_pgkbId);
  }

  /**
   * Gets the CPIC ID of the drug
   */
  private String getCpicId() {
    return m_cpicId;
  }

  /**
   * Gets the PharmGKB ID of the drug
   */
  private String getPgkbId() {
    return m_pgkbId;
  }

  /**
   * Gets just the symbols of the related genes of the guideline. Calculated from data in the original guideline.
   */
  public Collection<String> getRelatedGeneSymbols() {
    return f_guidelines.stream()
        .flatMap((guidelineReport) -> guidelineReport.getRelatedGeneReports().stream())
        .map(GeneReport::getGene)
        .distinct()
        .sorted()
        .collect(Collectors.toList());
  }

  public Set<String> getRelatedDrugs() {
    return ImmutableSet.of(f_name);
  }

  public boolean isMatched() {
    return sf_notApplicableMatches.contains(getCpicId())
        || f_guidelines.stream().anyMatch(GuidelineReport::isMatched);
  }

  /**
   * Gets the URL for the whole annotation
   */
  public List<String> getUrls() {
    return f_urls;
  }

  @Override
  public int compareTo(DrugReport o) {
    return toString().compareToIgnoreCase(o.toString());
  }


  public List<MessageAnnotation> getMessages() {
    return m_messages;
  }

  public void addMessage(MessageAnnotation message) {
    m_messages.add(message);
  }

  public void addMessages(@Nullable Collection<MessageAnnotation> messages) {
    if (messages == null) {
      return;
    }

    // separate the general messages from specific genotype call messages
    messages.forEach(ma -> {
      if (ma.getExceptionType().equals(MessageAnnotation.TYPE_GENOTYPE)) {
        m_reportVariants.add(ma.getMatches().getVariant());
      }
      else {
        m_messages.add(ma);
      }
    });
  }

  /**
   * Gets list of variants to display as part of genotype for recommendation.
   */
  public List<String> getReportVariants() {
    return m_reportVariants;
  }

  public String toString() {
    return String.join(", ", getName());
  }

  /**
   * Gets the literature objects that are used for citation of this Guideline
   */
  public List<Publication> getCitations() {
    return f_citations;
  }

  public boolean isCpic() {
    return m_cpicId != null;
  }

  public boolean isDpwg() {
    return m_pgkbId != null;
  }

  public List<GuidelineReport> getGuidelines() {
    return f_guidelines;
  }

  public int getMatchedGroupCount() {
    return getGuidelines().stream().mapToInt(g -> g.getAnnotationGroups().size()).sum();
  }
}
