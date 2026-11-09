# Frequency dictionary data

Source: https://github.com/hermitdave/FrequencyWords/tree/525f9b560de45753a5ea01069454e72e9aa541c6/content/2018
Author: Hermit Dave. Corpus: OpenSubtitles 2018.
License: CC BY-SA 4.0, https://creativecommons.org/licenses/by-sa/4.0/
Eight lists are included: English, Russian, French, German, Hindi, Arabic, Turkish and Spanish. Each contains 10,000 unique entries in corpus frequency order.

English and Russian keep the earlier alphabetic selection. The six additional lists use NFC normalization and language-specific lowercase. They retain letters and combining marks in the writing system of the layout, plus internal apostrophes for Latin-script languages. Entries are 2–24 UTF-16 units long.

To reproduce the six additional lists, download the files named <lang>_full.txt from the pinned source above and put them in one directory. Run node scripts/update-dictionaries.mjs <directory> from the project root. The word lists remain CC BY-SA 4.0. Application code is MIT.
