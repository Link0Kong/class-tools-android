"""Build the offline city and county catalogue from GeoNames CN.zip (CC BY 4.0)."""

import argparse
import re
import zipfile
from pathlib import Path


def chinese_name(row):
    names = [name for name in (row[1] + "," + row[3]).split(",") if re.fullmatch(r"[\u3400-\u9fff]{2,40}", name)]
    if names:
        endings = ("特别行政区", "自治区", "自治州", "自治县", "地区", "省", "市", "县", "区", "旗", "盟")
        return next((name for name in names if name.endswith(endings)), names[0])
    return row[1]


def build(package, output):
    levels = {"ADM1": {}, "ADM2": {}, "ADM3": {}}
    seats = {}
    with zipfile.ZipFile(package) as archive:
        with archive.open("CN.txt") as rows:
            for raw in rows:
                record = raw.decode("utf-8").rstrip("\r\n").split("\t")
                feature = record[7]
                if feature in levels:
                    levels[feature][record[0]] = record
                if feature in ("PPLA", "PPLA2", "PPLA3"):
                    index = {"PPLA": 11, "PPLA2": 12, "PPLA3": 13}[feature]
                    key = tuple(record[10:index])
                    if key not in seats or int(record[14] or 0) > int(seats[key][14] or 0):
                        seats[key] = record

    provinces = {row[10]: chinese_name(row) for row in levels["ADM1"].values()}
    cities = {(row[10], row[11]): chinese_name(row) for row in levels["ADM2"].values()}
    regions = []
    for level, rows in levels.items():
        for region_id, row in rows.items():
            if region_id == "1800476":  # The dedicated Minhou entry uses the reference weather station.
                continue
            name = chinese_name(row)
            if level == "ADM1" and name not in ("北京市", "天津市", "上海市", "重庆市"):
                continue
            parents = () if level == "ADM1" else (provinces.get(row[10]),)
            if level == "ADM3":
                parents += (cities.get((row[10], row[11])),)
            parent = " · ".join(dict.fromkeys(item for item in parents if item and item != name))
            index = {"ADM1": 11, "ADM2": 12, "ADM3": 13}[level]
            point = seats.get(tuple(row[10:index]), row)
            if not 17 <= float(point[4]) <= 55 or not 73 <= float(point[5]) <= 136:
                continue
            regions.append((parent, name, region_id, float(point[4]), float(point[5])))
    lines = [
        "# GeoNames China extract https://www.geonames.org/ — CC BY 4.0 https://creativecommons.org/licenses/by/4.0/",
        "# Filtered to city/county administrative areas with representative coordinates rounded to two decimals.",
    ]
    lines += [f"{region}\t{name}\t{parent}\t{lat:.2f}\t{lon:.2f}" for parent, name, region, lat, lon in sorted(regions)]
    output.write_text("\n".join(lines) + "\n", encoding="utf-8", newline="\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    build(args.source, args.output)
