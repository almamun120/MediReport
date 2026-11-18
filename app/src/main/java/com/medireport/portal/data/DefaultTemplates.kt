package com.medireport.portal.data

object DefaultTemplates {
    private fun t(name: String, title: String, specimen: String, body: String) =
        Template(name = name, title = title, specimen = specimen, content = body.trimIndent().trim())

    fun all(): List<Template> = listOf(
        t(
            "Haematology (CBC)", "HAEMATOLOGY REPORT", "BLOOD",
            """
            # Red Blood Cells
            Haemoglobin | gm/dl | M: 13 - 18 gm/dl, F: 11.5 - 16.5 gm/dl, Child: 10.0 - 13.0 gm/dl, Infant (01 Year) 8.0 - 10.0 gm/dl
            ESR (Westergren) | mm /hr | M: 0 - 10, F: 0 - 20 mm/hr.
            # White Blood Cells
            Total WBC Count | /Cumm | Adult: 4,000-11,000/cumm, Child: 5,000-15,000/cumm, Infant (1Yr.): 6,000-18,000/cumm
            # Differential Count
            Neutrophils | % | Child: 25-66%, Adult: 40-75%
            Lymphocytes | % | Child: 52-62%, Adult: 20-50%
            Monocytes | % | Child: 03-07%, Adult: 02-10%
            Eosinophils | % | Child: 01-03%, Adult: 01-06%
            Basophils | % | <1.0 %
            # RBC Panel
            Total RBC Count | m/ul | M: 4.5 - 6.5, F: 3.8 - 5.8 m/ul
            HCT | % | M: 40 - 54 %, F: 37 - 47 %
            MCV | fL | 76-94 fL
            MCH | pg | 27-32 pg
            MCHC | g/dL | 29-34 g/dL
            PDW | fL | 9 - 17 fL
            RDW | % | 11 - 16 %
            # PLT Panel
            Total Platelet Count | /Cumm | 1,50,000 - 4,50,000/Cumm
            MPV | fL | 7 - 11 fL
            PCT | % | 0.200 - 0.500 %
            """
        ),
        t(
            "Urine R/E", "URINE EXAMINATION REPORT", "URINE",
            """
            # PHYSICAL EXAMINATION
            Quantity | | | Sufficient
            Colour | | | Straw
            Appearance | | | Clear
            Sediment | | | Nil
            # CHEMICAL EXAMINATION
            Reaction | | | Acidic
            Excess of phosphate | | | Nil
            Albumin | | | Nil
            Sugar (Reducing substance) | | | Nil
            # MICROSCOPIC EXAMINATION
            Epithelial cells | /HPF | | 1-3
            RBC | | | Nil
            Pus cells | /HPF | | 0-2
            Amor phosphate | | | Nil
            Micro Organism | | | Nil
            Crystals | | | Nil
            Calcium oxalate | | | Nil
            Uric acid Crystals | | | Nil
            Urates | | | Nil
            Triple phosphate | | | Nil
            Sulphonomide crystals | | | Nil
            Spermatozoa(Sperm) | | | Nil
            Yeast cells | | | Nil
            Bacteria(non specific) | | | Nil
            """
        ),
        t(
            "Biochemistry (RBS & RFT)", "BIOCHEMISTRY REPORT", "BLOOD",
            """
            # Plasma Glucose & Corr. Urine Sugar
            B.Sugar Random (R.B.S) | mmol/L | < 7.8 mmol/L
            Corresponding Urine Sugar | | |
            # RFT
            S. Creatinine | mg/dl | 0.6-1.4 mg/dl
            S. Uric Acid | mg/dl | Male : 3.4-7.0\nFemale : 2.4-5.7 mg/dl
            """
        ),
        t(
            "Lipid Profile", "BIOCHEMISTRY REPORT", "BLOOD",
            """
            # Lipid Profile
            Total Cholesterol | mg/dl | < 200 mg/dl
            Triglycerides | mg/dl | < 150 mg/dl
            HDL Cholesterol | mg/dl | > 40 mg/dl
            LDL Cholesterol | mg/dl | < 100 mg/dl
            """
        ),
        t(
            "Immunology – TSH", "IMMUNOLOGY REPORT", "BLOOD",
            """
            TSH | µIU/ml | Adult:0.27-5.01\nChild:0.70-5.70\nInfant:0.80-9.10\nNewborn:1.0-39.0
            """
        ),
        t(
            "Dengue NS1 Ag (ICT)", "IMMUNOLOGY REPORT", "BLOOD",
            """
            Dengue NS1 Ag(ICT) | | |
            """
        ),
        t(
            "X-Ray Report", "X-RAY REPORT", "",
            """
            @ Examination
            @ Findings
            @ Impression
            """
        ),
        t(
            "General Report", "MEDICAL REPORT", "",
            """
            @ Report title
            @ Report details
            """
        )
    )
}
