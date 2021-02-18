#! /usr/bin/env python
__author__ = 'BinglanLi'

import os
import subprocess
import shutil
import urllib.request as request
from contextlib import closing
import  vcf_preprocess_exceptions as Exceptions

    ## "bcftools annotate <options> <input_vcf>" annotates and converts the VCF column info. "--rename-chrs <chr_name_mapping_file>" convert chromosome names from the old ones to the new ones.
    ## "bcftools merge <options> <input_vcf>" merge multiple VCF/BCF files to create one multi-sample file. "-m both" allows both SNP and indel records to be multiallelic (as expected by PharmCAT).
    ## "bcftools norm <options> <input_vcf>" normalizes the input VCF file. "-m+" join biallelic sites into multiallelic records. "-f" reference genome sequence in fasta format.

def obtain_vcf_file_prefix(path):
    vcf_file_full_name = os.path.split(path)[1].split('.')
    if (vcf_file_full_name[-2] == 'vcf') and (vcf_file_full_name[-1] == 'gz'):
        vcf_file_prefix = '.'.join(vcf_file_full_name[:len(vcf_file_full_name) - 2])
        return vcf_file_prefix
    else:
        raise Exceptions.InappropriateVCFSuffix(path)


def download_from_url(url, download_to_dir, save_to_file = None):
    local_filename = os.path.join(download_to_dir, url.split('/')[-1]) if not save_to_file else save_to_file
    with closing(request.urlopen(url)) as r:
        with open(local_filename, 'wb') as f:
            print('Downloading %s' %local_filename)
            shutil.copyfileobj(r, f)
    return local_filename


def download_grch38_ref_fasta_and_index(download_to_dir, save_to_file = None):
    # download the human reference genome sequence GRChh38/hg38 from the NIH FTP site

    # download ftp web address (dated Feb 2021)
    url_grch38_fasta = 'ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCA/000/001/405/GCA_000001405.15_GRCh38/seqs_for_alignment_pipelines.ucsc_ids/GCA_000001405.15_GRCh38_no_alt_analysis_set.fna.gz'
    url_grch38_fasta_index = 'ftp://ftp.ncbi.nlm.nih.gov/genomes/all/GCA/000/001/405/GCA_000001405.15_GRCh38/seqs_for_alignment_pipelines.ucsc_ids/GCA_000001405.15_GRCh38_no_alt_analysis_set.fna.fai'
    
    # download and prepare files for vcf normalization using the bcftools
    path_to_ref_seq = download_from_url(url_grch38_fasta, download_to_dir)
    subprocess.run(['gunzip', path_to_ref_seq], cwd = download_to_dir)
    download_from_url(url_grch38_fasta_index, download_to_dir)
    return os.path.splitext(path_to_ref_seq)[0]


def tabix_index_vcf(tabix_executable_path, vcf_path):
    # index the input vcf using tabix, and the output index file will be written to the working directory

    #####################
    # tabix commands are exclusively "tabix -p vcf <input_vcf>", which generates an index file (.tbi) for an input file (<input_file>) whose file type is specified by "-p vcf". The .tabi is, by default, output to the current working directory. 
    #####################
    
    try:
        subprocess.run([tabix_executable_path, '-p', 'vcf', vcf_path], cwd = os.path.split(vcf_path)[0])
    except:
        import sys
        print('Error: cannot index the file: %s' %vcf_path)
        sys.exit(1)


def running_bcftools(list_bcftools_command, show_msg = None):
    # run the bcftools following the commands stored in the list_bcftools_command

    #####################
    # "bcftools <common_options> <input_vcf>". 
    # "-Oz" (capitalized letter O) specifies the output type as compressed VCF (z). "-o" writes to a file rather than to default standard output.
    # "--no-version" will cease appending version and command line information to the output VCF header.
    # "-s sample_ID(s)" comma-separated list of samples to include or exclude if prefixed with "^". 
    #####################

    #print("%s [ %s ]" % (show_msg, ' '.join(list_bcftools_command))) if show_msg else print("Running [ %s ]" % (' '.join(list_bcftools_command)))
    print("%s" % (show_msg)) if show_msg else print("Running [ %s ]" % (' '.join(list_bcftools_command)))
    subprocess.run(list_bcftools_command)


def obtain_vcf_sample_list(bcftools_executable_path, path_to_vcf):
    # obtain a list of samples from the input VCF

    #####################
    # "bcftools query <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "-l" list sample names and exit. Samples are delimited by '\n' and the last line ends as 'last_sample_ID\n\n'. 
    #####################
    
    vcf_sample_list = subprocess.check_output([bcftools_executable_path, 'query', '-l', path_to_vcf], universal_newlines=True).split('\n')[:-1] # remove the black line at the end
    vcf_sample_list.remove('PharmCAT')
    return vcf_sample_list # remove the '\n' at the end


def remove_vcf_and_index(path_to_vcf):
    # remove the compressed vcf as well as the index file

    try:
        os.remove(path_to_vcf)
        os.remove(path_to_vcf + '.tbi')
        print("Removed intermediate files:\n\t%s\n\t%s" %(path_to_vcf, path_to_vcf + '.tbi'))
    except OSError as error_remove_tmp:
        print("Error: %s : %s" % (path_to_vcf, error_remove_tmp.strerror))


def rename_chr(bcftools_executable_path, input_vcf, file_rename_chrs, path_output):
    # rename chromosomes in input vcf according to a chr-renaming mapping file

    #####################
    # "bcftools annotate <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "--rename-chrs" renames chromosomes according to the map in file_rename_chrs. 
    #####################

    bcftools_command_to_rename_chr = [bcftools_executable_path, 'annotate', '--no-version', '--rename-chrs', file_rename_chrs, '-Oz', '-o', path_output, input_vcf]
    running_bcftools(bcftools_command_to_rename_chr, show_msg = 'Renaming chromosome(s)')


def merge_vcfs(bcftools_executable_path, input_vcf, file_ref_pgx_vcf, path_output):
    # merge the input VCF with a reference VCF of PGx core allele defining positions to enforce the same variant representation format across files

    #####################
    # "bcftools merge <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "-m both" allows both SNP and indel records can be multiallelic. But SNP and indel will not be merged as one multiallelic record. 
    #####################

    bcftools_command_to_merge = [bcftools_executable_path, 'merge', '--no-version', '-m', 'both', '-Oz', '-o', path_output, input_vcf, file_ref_pgx_vcf]
    running_bcftools(bcftools_command_to_merge, show_msg = 'Enforcing the same variant representation as that in the reference PGx variant file')


def normalize_vcf(bcftools_executable_path, input_vcf, path_to_ref_seq, path_output):
    # normalize the input VCF against the human reference genome sequence GRCh38/hg38

    #####################
    # "bcftools norm <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "-m+" joins biallelic sites into multiallelic records (+). 
    # "-f <ref_seq_fasta>" reference sequence. Supplying this option turns on left-alignment and normalization.
    #####################

    bcftools_command_to_normalize_vcf = [bcftools_executable_path, 'norm', '--no-version', '-m+', '-Oz', '-o', path_output, '-f', path_to_ref_seq, input_vcf]
    running_bcftools(bcftools_command_to_normalize_vcf, show_msg = 'Normalize VCF') # run bcftools to merge VCF files


def output_pharmcat_ready_vcf(bcftools_executable_path, input_vcf, output_dir, output_prefix):
    # iteratively write to a PharmCAT-ready VCF for each sample

    #####################
    # "bcftools view <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "-U" exclude sites without a called genotype, i.e., GT = './.'
    #####################

    sample_list = obtain_vcf_sample_list(bcftools_executable_path, input_vcf)
    
    for single_sample in sample_list:
        output_file_name = os.path.join(output_dir, output_prefix + '.' + single_sample + '.vcf.gz')
        bcftools_command_to_output_pharmcat_ready_vcf = [bcftools_executable_path, 'view', '--no-version', '-U', '-Oz', '-o', output_file_name, '-s', single_sample, input_vcf]
        running_bcftools(bcftools_command_to_output_pharmcat_ready_vcf, show_msg = 'Generating a PharmCAT-ready VCF for ' + single_sample)


def output_missing_pgx_positions(bcftools_executable_path, input_vcf, file_ref_pgx_vcf, output_dir, output_prefix):
    # generate a report VCF of missing PGx positions from the input VCF against a reference PGx VCF

    #####################
    # "bcftools isec <options> <input_vcf>". For bcftools common options, see running_bcftools().
    # "-c" controls how to treat records with duplicate positions. "-c indels" means that all indel records are compatible, regardless of whether the REF and ALT alleles match or not. For duplicate positions, only the first indel record will be considered and appear on output.
    # "-C" outputs positions present only in the first file but missing in the others.
    # "-w" lists input files to output given as 1-based indices. "-w1" extracts and writes records from the first file, `file_ref_pgx_vcf`, as set by this program. 
    #####################

    output_file_name = os.path.join(output_dir, output_prefix + '.missing_pgx_var.vcf.gz')
    bcftools_command_to_report_missing_pgx = [bcftools_executable_path, 'isec', '--no-version', '-c', 'indels', '-w1', '-Oz', '-o', output_file_name, '-C', file_ref_pgx_vcf, input_vcf]
    running_bcftools(bcftools_command_to_report_missing_pgx, show_msg = 'Generating a report of missing PGx core allele defining positions')