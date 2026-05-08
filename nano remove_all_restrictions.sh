#!/data/data/com.termux/files/usr/bin/bash

cd ~/Kai || exit

# ملف القواعد فيه كل الكلمات اللي تعتبرها قيود والبدائل لها
rules_file="rules.txt"

# لو ما فيه ملف rules.txt، أنشئ واحد افتراضي
if [ ! -f "$rules_file" ]; then
cat <<EOL > $rules_file
restricted=free
disabled=enabled
internal=public
experimental=stable
private=public
limit=unlimited
blocked=allowed
EOL
fi

# استبدال كل القيود دفعة واحدة
while IFS='=' read -r find replace; do
    grep -rl "$find" . | xargs sed -i "s/$find/$replace/g"
done < "$rules_file"

# عرض الملفات المعدلة
git status

# رفع التعديلات مباشرة بخطوة واحدة
git add .
git commit -m "إزالة جميع القيود بشكل عام"
git push origin main
