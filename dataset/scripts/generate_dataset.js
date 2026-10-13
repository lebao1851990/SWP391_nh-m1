const fs = require('fs');
const path = require('path');

// Configuration
const SEED = 42; // For true deterministic JS math random we'd use a seedable rng, but for this simplified generator we'll just not use random since we iterate deterministically.

const BASE_DIR = path.dirname(__dirname);
const BASE_PROGRAMS_DIR = path.join(BASE_DIR, 'programs', 'base');
const GENERATED_DIR = path.join(BASE_DIR, 'programs', 'generated');
const GROUND_TRUTH_DIR = path.join(BASE_DIR, 'ground-truth');

// Create/clean dirs
if (fs.existsSync(BASE_PROGRAMS_DIR)) fs.rmSync(BASE_PROGRAMS_DIR, { recursive: true, force: true });
if (fs.existsSync(GENERATED_DIR)) fs.rmSync(GENERATED_DIR, { recursive: true, force: true });
if (fs.existsSync(GROUND_TRUTH_DIR)) fs.rmSync(GROUND_TRUTH_DIR, { recursive: true, force: true });

fs.mkdirSync(BASE_PROGRAMS_DIR, { recursive: true });
fs.mkdirSync(GENERATED_DIR, { recursive: true });
fs.mkdirSync(GROUND_TRUTH_DIR, { recursive: true });

// Documented implementation subset
const CATEGORIES = ['A', 'B', 'C', 'D', 'E1', 'E3', 'F1'];
const NUM_VARIANTS = 3;

let pair_id_counter = 1;

function generate_variant(base_code, category, class_name) {
    let code = base_code;
    
    // Always rename the class safely
    code = code.replace(/public class Base\d+/, `public class ${class_name}`);

    if (category === 'A') {
        // Exact copy except class name
    } else if (category === 'B') {
        // Safe identifier changes
        code = code.replace(/\bmyCounter\b/g, 'totalCount');
        code = code.replace(/\bloopVar\b/g, 'idx');
    } else if (category === 'C') {
        // Comments and whitespace changes
        code = code.replace(/\n/g, '\n/* inline obfuscation */\n');
        code = code.replace(/    /g, '\t');
    } else if (category === 'D') {
        // Literal changes targeted safely
        code = code.replace(/\b10\b/g, '100');
        code = code.replace(/"Result: "/g, '"Final Output: "');
    } else if (category === 'E1') {
        // E1 = dead_code_injection
        code = code.replace(';', ';\n        if(false){int dead_var=0;}'); // replaces first occurrence
    } else if (category === 'E3') {
        // E3 = block_wrapping
        code = code.replace('public static void main(String[] args) {', 'public static void main(String[] args) {\n        {');
        const lastBraceIdx = code.lastIndexOf('}');
        if (lastBraceIdx !== -1) {
            const prevBraceIdx = code.lastIndexOf('}', lastBraceIdx - 1);
            if (prevBraceIdx !== -1) {
                code = code.substring(0, prevBraceIdx) + '        }\n' + code.substring(prevBraceIdx);
            }
        }
    } else if (category === 'F1') {
        // F1 = for_to_while
        if (code.includes('for (int loopVar = 0; loopVar < 10; loopVar++) {')) {
            code = code.replace('for (int loopVar = 0; loopVar < 10; loopVar++) {', 'int loopVar = 0; while(loopVar < 10) {');
            code = code.replace('System.out.println("Result: " + myCounter);\n    }', 'System.out.println("Result: " + myCounter);\n        loopVar++;\n    }');
        }
    }
    
    return code;
}

function write_base_programs() {
    for (let i = 1; i <= 5; i++) {
        const base_name = `Base${i}.java`;
        const base_path = path.join(BASE_PROGRAMS_DIR, base_name);
        const content = `public class Base${i} {
    public static void main(String[] args) {
        int myCounter = 0;
        for (int loopVar = 0; loopVar < 10; loopVar++) {
            myCounter += loopVar;
        }
        System.out.println("Result: " + myCounter);
    }
}
`;
        fs.writeFileSync(base_path, content);
    }
}

function pad(num, size) {
    let s = num + "";
    while (s.length < size) s = "0" + s;
    return s;
}

function main() {
    write_base_programs();
    const base_files = fs.readdirSync(BASE_PROGRAMS_DIR).filter(f => f.endsWith('.java')).sort();
    
    let csvContent = 'pair_id,program_a,program_b,category,transformation,ground_truth\n';

    // Generate Variants & Positive Pairs
    for (const base of base_files) {
        const base_code = fs.readFileSync(path.join(BASE_PROGRAMS_DIR, base), 'utf-8');
        const base_idx = base.match(/\d+/)[0];

        for (const cat of CATEGORIES) {
            for (let v = 1; v <= NUM_VARIANTS; v++) {
                const variant_name = `Variant_${base_idx}_${cat}_${v}`;
                const variant_filename = `${variant_name}.java`;
                
                const variant_code = generate_variant(base_code, cat, variant_name);
                fs.writeFileSync(path.join(GENERATED_DIR, variant_filename), variant_code);

                csvContent += `pair_${pad(pair_id_counter, 4)},${base},${variant_filename},${cat},transformation_${cat},PLAGIARISM\n`;
                pair_id_counter++;
            }
        }
    }

    // Negative Pairs (Base vs Base)
    for (let i = 0; i < base_files.length; i++) {
        for (let j = i + 1; j < base_files.length; j++) {
            csvContent += `pair_${pad(pair_id_counter, 4)},${base_files[i]},${base_files[j]},G,unrelated_base,NON_PLAGIARISM\n`;
            pair_id_counter++;
        }
    }

    // Negative Pairs (Variant vs Unrelated Base)
    const generated_files = fs.readdirSync(GENERATED_DIR).filter(f => f.endsWith('.java')).sort();
    for (const var_file of generated_files) {
        const var_base_idx = var_file.split('_')[1];
        for (const base of base_files) {
            const base_idx = base.match(/\d+/)[0];
            if (var_base_idx !== base_idx) {
                csvContent += `pair_${pad(pair_id_counter, 4)},${base},${var_file},G,unrelated_base,NON_PLAGIARISM\n`;
                pair_id_counter++;
            }
        }
    }

    fs.writeFileSync(path.join(GROUND_TRUTH_DIR, 'pairs.csv'), csvContent);
    console.log("Dataset generation complete. Pairs written to pairs.csv.");
}

main();
