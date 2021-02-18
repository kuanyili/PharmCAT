#! /usr/bin/env python

__author__ = 'BinglanLi'

import os
import vcf_preprocess_utilities as Utilities

def run(args):
    ## this section normalizes and prepares the input VCF file for the PharmCAT

    # organize args
    current_working_dir = os.getcwd()
    tabix_executable_path = args.path_to_tabix if args.path_to_tabix else 'tabix'
    bcftools_executable_path = args.path_to_bcftools if args.path_to_bcftools else 'bcftools'

    tmp_files_to_be_removed = []

    # download the human reference sequence if not provided
    path_to_ref_seq = Utilities.download_grch38_ref_fasta_and_index(current_working_dir) if not args.ref_seq else args.ref_seq

    # if the input VCF file is not indexed (.tbi doesn't exist), create an index file in the input folder using tabix
    if not os.path.exists(args.input_vcf + '.tbi'):
        Utilities.tabix_index_vcf(tabix_executable_path, args.input_vcf)

    # rename chromosomes
    intermediate_vcf_renamed_chr = os.path.join(args.output_folder, Utilities.obtain_vcf_file_prefix(args.input_vcf) + '.chr_renamed.vcf.gz')
    Utilities.rename_chr(bcftools_executable_path, args.input_vcf, args.rename_chrs, intermediate_vcf_renamed_chr)
    Utilities.tabix_index_vcf(tabix_executable_path, intermediate_vcf_renamed_chr)
    tmp_files_to_be_removed.append(intermediate_vcf_renamed_chr)

    # merge the input VCF with the PGx position file provided by '--ref_pgx_vcf'
    # run this step to ensure the output VCF will have THE SAME VARIANT REPRESENTATION per PharmCAT's expectations
    intermediate_vcf_pgx_merged = os.path.join(args.output_folder, Utilities.obtain_vcf_file_prefix(intermediate_vcf_renamed_chr) + '.pgx_merged.vcf.gz')
    Utilities.merge_vcfs(bcftools_executable_path, intermediate_vcf_renamed_chr, args.ref_pgx_vcf, intermediate_vcf_pgx_merged)
    Utilities.tabix_index_vcf(tabix_executable_path, intermediate_vcf_pgx_merged)
    tmp_files_to_be_removed.append(intermediate_vcf_pgx_merged)

    # normalize the input VCF; and extract only the PGx positions in the 'ref_pgx_vcf' file
    # modify this part to comply to the PharmCAT VCF requirements and PharmCAT only
    intermediate_vcf_pgx_merged_normalized = os.path.join(args.output_folder, Utilities.obtain_vcf_file_prefix(intermediate_vcf_pgx_merged) + '.normalized.vcf.gz')
    Utilities.normalize_vcf(bcftools_executable_path, intermediate_vcf_pgx_merged, path_to_ref_seq, intermediate_vcf_pgx_merged_normalized)
    Utilities.tabix_index_vcf(tabix_executable_path, intermediate_vcf_pgx_merged_normalized)
    tmp_files_to_be_removed.append(intermediate_vcf_pgx_merged_normalized)

    # iteratively output each sample into a single-sample PharmCAT-ready VCF
    Utilities.output_pharmcat_ready_vcf(bcftools_executable_path, intermediate_vcf_pgx_merged_normalized, args.output_folder, args.output_prefix)

    # generate a report of missing PGx positions in VCF format
    Utilities.output_missing_pgx_positions(bcftools_executable_path, intermediate_vcf_pgx_merged_normalized, args.ref_pgx_vcf, args.output_folder, args.output_prefix)

    # remove intermediate files
    for single_path in tmp_files_to_be_removed:
       Utilities.remove_vcf_and_index(single_path)


if __name__ == "__main__":
    import argparse

    # describe the 
    parser = argparse.ArgumentParser(description='Prepare an input VCF for the PharmCAT')

    # list arguments
    parser.add_argument("--input_vcf", required=True, type = str, help="Load a compressed VCF file.")
    parser.add_argument("--ref_seq", help="Load the Human Reference Genome GRCh38/hg38 in the fasta format.")
    parser.add_argument("--rename_chrs", required=True, type = str, help="Load a chromosome rename map file. This is a must for VCF normalization if the chromosomes are not named 'chr##'.")
    parser.add_argument("--ref_pgx_vcf", required=True, type = str, help="Load a VCF file of PGx variants. This file is available from the PharmCAT GitHub release.")
    parser.add_argument("--path_to_bcftools", help="Load an alternative path to the executable bcftools.")
    parser.add_argument("--path_to_tabix", help="Load an alternative path to the executable tabix.")
    parser.add_argument("--output_folder", default = os.getcwd(), type = str, help="Directory of the output VCF, by default, current working directory.")
    parser.add_argument("--output_prefix", default = 'pharmcat_ready_vcf', type = str, help="Prefix of the output VCF")

    # parse arguments
    args = parser.parse_args()

    # normalize variant representations and reconstruct multi-allelic variants in the input VCF
    run(args)
