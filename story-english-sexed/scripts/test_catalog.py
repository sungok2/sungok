import json,unittest
from pathlib import Path
BASE=Path(__file__).resolve().parent.parent
class TestSourceCatalog(unittest.TestCase):
 def setUp(self):self.rows=json.loads((BASE/'catalog/asset_sources.json').read_text())
 def test_size(self):self.assertGreaterEqual(len(self.rows),15)
 def test_ids_unique(self):self.assertEqual(len(self.rows),len({e['id'] for e in self.rows}))
 def test_filenames_unique(self):self.assertEqual(len(self.rows),len({e['filename'] for e in self.rows}))
 def test_allowed_licenses(self):
  for e in self.rows:self.assertIn(e['license'],['CC BY-SA 4.0','CC BY-SA 3.0','CC BY 2.0','CC0 1.0','PD-USGov'])
 def test_sources_specific(self):
  for e in self.rows:
   for key in ['author','teaching_en','teaching_ko','topic','license_url']:self.assertTrue(e[key])
 def test_no_platform_scraping(self):
  t=(BASE/'scripts/acquire.py').read_text();self.assertIn('upload.wikimedia.org',t);self.assertNotIn('pornhub',t.lower())
if __name__=='__main__':unittest.main()