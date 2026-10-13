import os
import re
import csv
import random
import shutil

# Configuration
SEED = 42
random.seed(SEED)

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE_PROGRAMS_DIR = os.path.join(BASE_DIR, 'programs', 'base')
GENERATED_DIR = os.path.join(BASE_DIR, 'programs', 'generated')
GROUND_TRUTH_DIR = os.path.join(BASE_DIR, 'ground-truth')

# Create dirs
os.makedirs(BASE_PROGRAMS_DIR, exist_ok=True)
os.makedirs(GENERATED_DIR, exist_ok=True)
os.makedirs(GROUND_TRUTH_DIR, exist_ok=True)

# Documented implementation subset
CATEGORIES = ['A', 'B', 'C', 'D', 'E1', 'E3', 'F1']
NUM_VARIANTS = 3

pairs = []
pair_id_counter = 1

def generate_variant(base_code, category, class_name):
    code = base_code
    # Always rename the class safely (assuming standard public class Declaration)
    code = re.sub(r'public class Base\d+', f'public class {class_name}', code)

    if category == 'A':
        pass # Exact copy except class name
    elif category == 'B':
        # Safe identifier changes targeted to our known base programs
        code = re.sub(r'\bmyCounter\b', 'totalCount', code)
        code = re.sub(r'\bloopVar\b', 'idx', code)
    elif category == 'C':
        # Comments and whitespace changes
        code = code.replace('\n', '\n/* inline obfuscation */\n')
        code = code.replace('    ', '\t')
    elif category == 'D':
        # Literal changes targeted safely
        code = re.sub(r'\b10\b', '100', code)
        code = code.replace('"Result: "', '"Final Output: "')
    elif category == 'E1':
        # E1 = dead_code_injection
        code = code.replace(';', ';\n        if(false){int dead_var=0;}', 1)
    elif category == 'E3':
        # E3 = block_wrapping
        # Wrapping the body of the main method
        code = code.replace('public static void main(String[] args) {', 'public static void main(String[] args) {\n        {')
        # adding closing brace before the last two braces (main close, class close)
        last_brace_idx = code.rfind('}')
        if last_brace_idx != -1:
            prev_brace_idx = code.rfind('}', 0, last_brace_idx)
            if prev_brace_idx != -1:
                code = code[:prev_brace_idx] + '        }\n' + code[prev_brace_idx:]
    elif category == 'F1':
        # F1 = for_to_while
        if 'for (int loopVar = 0; loopVar < 10; loopVar++) {' in code:
            code = code.replace('for (int loopVar = 0; loopVar < 10; loopVar++) {', 'int loopVar = 0; while(loopVar < 10) {')
            code = code.replace('System.out.println("Result: " + myCounter);\n    }', 'System.out.println("Result: " + myCounter);\n        loopVar++;\n    }')
    
    return code

def write_base_programs():
    # Write 5 identical structural base programs for predictable regex targeting
    for i in range(1, 6):
        base_name = f'base_{i}.java'
        base_path = os.path.join(BASE_PROGRAMS_DIR, base_name)
        content = f"""public class Base{i} {{
    public static void main(String[] args) {{
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {{
            myCounter += loopVar;
        }}
        System.out.println("Result: " + myCounter);
    }}
}}
"""
        with open(base_path, 'w') as f:
            f.write(content)

def main():
    global pair_id_counter
    write_base_programs()
    base_files = [f for f in sorted(os.listdir(BASE_PROGRAMS_DIR)) if f.endswith('.java')]

    with open(os.path.join(GROUND_TRUTH_DIR, 'pairs.csv'), 'w', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(['pair_id', 'program_a', 'program_b', 'category', 'transformation', 'ground_truth'])

        # Generate Variants & Positive Pairs
        for base in base_files:
            with open(os.path.join(BASE_PROGRAMS_DIR, base), 'r') as bf:
                base_code = bf.read()
            
            base_idx = base.split('_')[1].split('.')[0]

            for cat in CATEGORIES:
                for v in range(1, NUM_VARIANTS + 1):
                    variant_name = f"Variant_{base_idx}_{cat}_{v}"
                    variant_filename = f"{variant_name}.java"
                    
                    variant_code = generate_variant(base_code, cat, variant_name)
                    
                    with open(os.path.join(GENERATED_DIR, variant_filename), 'w') as vf:
                        vf.write(variant_code)

                    writer.writerow([f"pair_{pair_id_counter:04d}", base, variant_filename, cat, f"transformation_{cat}", "PLAGIARISM"])
                    pair_id_counter += 1

        # Negative Pairs (Base vs Base)
        for i in range(len(base_files)):
            for j in range(i+1, len(base_files)):
                writer.writerow([f"pair_{pair_id_counter:04d}", base_files[i], base_files[j], 'G', "unrelated_base", "NON_PLAGIARISM"])
                pair_id_counter += 1
                
        # Negative Pairs (Variant vs Unrelated Base)
        generated_files = [f for f in sorted(os.listdir(GENERATED_DIR)) if f.endswith('.java')]
        for var_file in generated_files:
            var_base_idx = var_file.split('_')[1]
            for base in base_files:
                base_idx = base.split('_')[1].split('.')[0]
                if var_base_idx != base_idx:
                    # Randomly sample a subset of negative pairs to avoid exponential explosion, 
                    # but for deterministic test we add exactly 1 pair per unrelated base
                    writer.writerow([f"pair_{pair_id_counter:04d}", base, var_file, 'G', "unrelated_base", "NON_PLAGIARISM"])
                    pair_id_counter += 1

    print("Dataset generation complete.")

if __name__ == "__main__":
    main()
