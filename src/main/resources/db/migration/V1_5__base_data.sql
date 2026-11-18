-- The spex, spex categories, tasks and task categories of FGV: base data that every environment starts with, with fixed
-- ids that the other applications and the content migration rely on. Compiled from Spexregister's former lists and
-- checked against the archive (its spex folders, Information and Poster i Spexet sheets) and the old MediaWiki's
-- infoboxes. A revival is a spex with a parent; its title and category are its parent's. The change feed entries and
-- search documents are written at start (SeededBaseData), from the same records the services would write.

INSERT INTO spex_category (uuid, name, first_year, created_by, created_at)
VALUES ('e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8', 'Gamla Chalmersspexet', '1948', 'system', NOW(6)),
       ('fa657077-b1bf-3e6f-9433-a84dd472f2a7', 'Bobspexet', '2003', 'system', NOW(6)),
       ('17fcc848-4bbb-3c9d-988e-1ec9c76f85af', 'Veraspexet', '2003', 'system', NOW(6)),
       ('5e9f6265-3589-3256-ab1c-4e4f05df622d', 'Jubileumsspex', '1948', 'system', NOW(6)),
       ('ab9f4952-1af6-3b23-808a-8b07c54f4173', 'Nya Chalmersspexet', '2020', 'system', NOW(6));

INSERT INTO spex (uuid, slug, year, title, category_id, created_by, created_at)
VALUES ('c733337b-1a27-3264-a00f-dac3ab13e661', '1948-bojan', '1948', 'Bojan', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('3d8bbedd-41e3-3402-8268-a3e8b095e54c', '1949-erik-xiv', '1949', 'Erik XIV', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('36beacff-88b1-3149-b1f9-ae1874f10f50', '1950-caesarion', '1950', 'Cæsarion', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('7debec4e-3da4-3041-b9a8-02bf12a156a7', '1951-scheherazade', '1951', 'Scheherazade', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('a89657c9-6225-3100-97e7-77ca9c690d7e', '1952-anna', '1952', 'Anna', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('0bbdcf2a-c1a7-36dc-b055-7bdb444353a0', '1953-caesarion', '1953', 'Cæsarion', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('5400a33b-cf1a-3af1-94b9-2c14b38d311b', '1954-henrik-8', '1954', 'Henrik 8', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('c3804a4d-3c22-389b-b8ec-bcdae27247eb', '1955-gustaf-e-son-vasa', '1955', 'Gustaf E:son Vasa', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('80fefd6f-ab6f-330e-b621-ba9456932e3a', '1956-napoleon', '1956', 'Napoleon', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('cc83ec83-6c8d-3fbc-97d5-2afbfb86a829', '1957-statyerna', '1957', 'Statyerna', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('ae1513cf-1cc3-38b6-be32-f71f97fb21d4', '1958-lucrezia', '1958', 'Lucrezia', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('fb1941c2-7acf-33cc-b010-2d061de29d5d', '1959-katarina-ii', '1959', 'Katarina II', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('d217421c-2c9d-32e4-8d9c-60dab4bd1b71', '1960-starke-august', '1960', 'Starke August', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('1612d33f-d6c5-33a2-bee0-0060aae02aa2', '1961-klodvig', '1961', 'Klodvig', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('c8bb4f61-a9fe-34fa-8d27-75c4b16ffe5f', '1962-don-pedro', '1962', 'Don Pedro', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('c37b7433-8b5c-3519-869c-5e98681c533f', '1963-charles-ii', '1963', 'Charles II', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('556c87ac-943b-3e52-a4ce-84ffaf73e826', '1964-nebukadnesar', '1964', 'Nebukadnesar', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('4687283b-3bdf-37c3-99d9-73b06410613a', '1965-sven-duva', '1965', 'Sven Duva', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('007223b2-2d66-3df9-8a9d-dabe7dba1f69', '1966-montezuma', '1966', 'Montezuma', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('2f851e18-f58d-3858-bf1d-05201040146d', '1967-alexander', '1967', 'Alexander', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('5fdc96d4-47c7-369c-8628-36d614972cd3', '1968-richard-iii', '1968', 'Richard III', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('9e1af41f-1639-3cbe-bd46-0476131dc47f', '1969-margareta', '1969', 'Margareta', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('2e2d3e8f-c750-3955-9517-048133768f98', '1970-george-washington', '1970', 'George Washington', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('eb00c43f-f368-31cc-a4c2-8e7a17add042', '1971-noak', '1971', 'Noak', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('b50f813d-2dbc-3264-87e1-679cd113f8d9', '1972-turandot', '1972', 'Turandot', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('11b4c503-169e-3a7d-b2c3-c54386c0d615', '1973-fredrik-den-store', '1973', 'Fredrik den Store', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('1f8fb516-1be0-321c-baf6-ef08382e541e', '1973-25-arsjubileet', '1973', '25-årsjubileet', (SELECT id FROM spex_category WHERE uuid = '5e9f6265-3589-3256-ab1c-4e4f05df622d'), 'system', NOW(6)),
       ('a6ef75df-0565-3a66-9c24-cb5fe4c0d824', '1974-sherlock-holmes', '1974', 'Sherlock Holmes', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('112927e0-072f-3898-83f8-e5ab7562310c', '1975-lionardo-da-vinci', '1975', 'Lionardo da Vinci', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('357c6558-451b-38ea-953c-a4e3af2479e6', '1976-ludvig-xiv', '1976', 'Ludvig XIV', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('db47475a-1a7e-3b10-826e-d80c1b590a2e', '1977-nils-dacke', '1977', 'Nils Dacke', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('a5a2ec6f-bab1-339d-83eb-1aed8be77dc5', '1978-dr-livingstone', '1978', 'Dr. Livingstone', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('b5e4fcfb-557d-3ba4-acbb-edbaff4abe51', '1979-nero', '1979', 'Nero', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('04844c74-59a8-3521-bc9b-d5d0854edb02', '1979-knappt-ett-chalmersspex', '1979', 'Knappt ett Chalmersspex', (SELECT id FROM spex_category WHERE uuid = '5e9f6265-3589-3256-ab1c-4e4f05df622d'), 'system', NOW(6)),
       ('c657764e-de66-3ea9-b697-9dbb5b64c44f', '1980-tutankhamon', '1980', 'Tutankhamon', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('8d2439ee-676f-3c11-8a58-4a62b8468a64', '1981-ludwig-van-beethoven', '1981', 'Ludwig van Beethoven', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('23802256-46be-3f52-81c2-8dc6ad6a8792', '1982-john-ericsson', '1982', 'John Ericsson', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('c9b7a708-46ab-37a5-9f44-589687e0cb3a', '1983-filip-ii', '1983', 'Filip II', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('857c6791-4659-3e6b-bb24-002312ce420b', '1984-lasse-maja', '1984', 'Lasse-Maja', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('4a4ef979-b11e-3b14-b264-e3bfd468c580', '1985-olof-skotkonung', '1985', 'Olof Skötkonung', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('b0f1ce7a-6937-345e-b1e0-f7d6bd1e7285', '1986-victoria', '1986', 'Victoria', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('337a62c3-ab48-3bd4-9d6c-e767624b7eeb', '1987-montgomery', '1987', 'Montgomery', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('0d71786d-1d91-39d2-b712-3488386c75bb', '1988-svartskagg', '1988', 'Svartskägg', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('319fd3f8-16fd-3bd7-8dbb-6f9e19865df3', '1989-christina', '1989', 'Christina', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('22916c07-04fc-3b3f-8a07-b1529774417e', '1990-klondike', '1990', 'Klondike', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('ca6307cb-ec13-3551-bd3e-d8bda974315d', '1991-gutenberg', '1991', 'Gutenberg', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('e77b28a4-1dc2-3155-9583-fc0363d2b8ce', '1992-krosus', '1992', 'Krösus', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('152dfcdc-d576-3c76-85c9-2d7244347c3f', '1993-stradivarius', '1993', 'Stradivarius', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('fd14bceb-8876-38f7-8d98-3db1f1af2696', '1994-ivan-den-forskracklige', '1994', 'Ivan den förskräcklige', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('83327344-edb0-313f-94d8-6607fce5f8ed', '1995-snorre', '1995', 'Snorre', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('687add6d-1e72-3cdd-bdcb-5b6f18e663cf', '1996-nobel', '1996', 'Nobel', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('7a48f699-09a3-3d63-ace6-d7649b875f33', '1997-ali-baba', '1997', 'Ali Baba', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('c973c928-a12f-3ef7-b426-105105611094', '1998-skona-helena', '1998', 'Sköna Helena', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('8dc4b24d-727e-3f64-b65b-14ee2a5a1d38', '1998-50-arskavalkad', '1998', '50-årskavalkad', (SELECT id FROM spex_category WHERE uuid = '5e9f6265-3589-3256-ab1c-4e4f05df622d'), 'system', NOW(6)),
       ('48b49daf-6dc4-3f7b-8b1a-c67409d63941', '1999-nostradamus', '1999', 'Nostradamus', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('4c469f0e-23aa-3742-9dfd-a602b2356f3d', '2000-mose', '2000', 'Mose', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('b945e735-e44b-377e-b1ac-4cec30c44432', '2001-marco-polo', '2001', 'Marco Polo', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('26c072f1-848d-362c-8668-8ee993c5f7f2', '2002-dracula', '2002', 'Dracula', (SELECT id FROM spex_category WHERE uuid = 'e9bd2ef4-af0e-376a-91a4-9b20a1fd67c8'), 'system', NOW(6)),
       ('7f749429-11a4-3a84-8458-2ae965672002', '2003-gagarin', '2003', 'Gagarin', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('e3133b1c-9e73-3f65-94ee-da4acf7912c5', '2003-mata-hari', '2003', 'Mata Hari', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('f42ff300-782f-3875-b5c2-e3e5072bfa8e', '2004-heliga-birgitta', '2004', 'Heliga Birgitta', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('31a81202-ee6a-3bca-8f6f-92829ad0a48e', '2004-phileas-fogg', '2004', 'Phileas Fogg', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('ba87cc02-b078-3601-8b1f-8a3a39c03457', '2005-gustav-ii-adolf', '2005', 'Gustav II Adolf', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('d24d5735-a95e-3cc4-80ce-112a9965b4ba', '2005-arthur', '2005', 'Arthur', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('62b8719f-b824-3733-a65c-f7d433dd13b3', '2006-casanova', '2006', 'Casanova', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('b16c11e8-3eff-333c-8dad-45a60c687b38', '2006-amelia-earhart', '2006', 'Amelia Earhart', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('432bd4be-69ed-3221-9c67-603811d8f831', '2007-hannibal', '2007', 'Hannibal', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('1f72204b-48fd-3e8c-9df2-6eb328eef7d7', '2007-frankenstein', '2007', 'Frankenstein', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('1e09508a-4f9d-37e6-a1ed-9c9c87103da4', '2008-picasso', '2008', 'Picasso', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('5dd796e6-3ce1-34a9-845a-3aa10f875f74', '2008-wyatt-earp-doc-holliday', '2008', 'Wyatt Earp & Doc Holliday', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('4f8dfa2e-3428-3655-b07e-9d4f8d8d6551', '2008-tender-bar', '2008', 'Tender Bar', (SELECT id FROM spex_category WHERE uuid = '5e9f6265-3589-3256-ab1c-4e4f05df622d'), 'system', NOW(6)),
       ('a2e0d7ce-2c35-3327-b672-87fc06cc98ac', '2009-newton', '2009', 'Newton', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('fffe8725-0cb1-3012-8fdf-dba878c87637', '2009-taj-mahal', '2009', 'Taj Mahal', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('70b97150-d827-389f-af88-451979448dbd', '2010-kristian-tyrann', '2010', 'Kristian Tyrann', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('b418b0b4-1e12-33f1-99a2-d97f6fad77a3', '2010-lucia', '2010', 'Lucia', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('5198378d-0bea-328e-8683-9350b6b13fb9', '2011-marie-curie', '2011', 'Marie Curie', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('5c736182-c698-3e47-a889-11397a0d7b79', '2011-karl-xii', '2011', 'Karl XII', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('d532ec3b-3f09-3fbd-b84c-67929b319cf6', '2012-christofer-columbus', '2012', 'Christofer Columbus', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('d6f13141-96f8-3789-8a95-491534bc5b96', '2012-broderna-lumiere', '2012', 'Bröderna Lumière', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('243d64bb-938b-36a0-a512-7533e14d9e0a', '2013-bellman', '2013', 'Bellman', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('7fae92ab-af49-37aa-bbaa-ed7d0888d87c', '2013-zheng', '2013', 'Zheng', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('1b671973-0ff5-3e1b-9913-29e3a8e60f36', '2014-herakles', '2014', 'Herakles', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('547c9be4-97b7-3782-bfee-420503fef010', '2014-jeanne-d-arc', '2014', 'Jeanne d''Arc', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('46c25cb6-5d96-394d-9c9f-7e8b97747a40', '2015-rasputin', '2015', 'Rasputin', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('580212a1-715a-3dbe-870a-699900f877ab', '2015-anne-bonny', '2015', 'Anne Bonny', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('c5c35f3c-a036-3b70-8f3e-c848f8a96497', '2016-kleopatra', '2016', 'Kleopatra', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('88f0631f-439b-3a5e-8ac6-1896bed272b2', '2016-al-capone', '2016', 'Al Capone', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('dc2ec110-18d0-3c03-8a08-01e8e038fc05', '2017-magnus-ladulas', '2017', 'Magnus Ladulås', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('8f0ac48c-171b-3fee-b085-bdc221140663', '2017-michelangelo', '2017', 'Michelangelo', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('d4e60070-6639-3305-8f02-d9b90d3a3d19', '2018-geronimo', '2018', 'Geronimo', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('feda8ce1-7823-333d-9aba-f3890c6057f8', '2018-ada-lovelace', '2018', 'Ada Lovelace', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('fe82cecf-61cc-3830-95e8-6c3196226709', '2019-marie-antoinette', '2019', 'Marie Antoinette', (SELECT id FROM spex_category WHERE uuid = 'fa657077-b1bf-3e6f-9433-a84dd472f2a7'), 'system', NOW(6)),
       ('b5f1b3b3-bb0c-35ee-a68b-c3a26b8fd0ef', '2019-karin-mansdotter', '2019', 'Karin Månsdotter', (SELECT id FROM spex_category WHERE uuid = '17fcc848-4bbb-3c9d-988e-1ec9c76f85af'), 'system', NOW(6)),
       ('daad89d2-92af-3874-9047-705a9c7caed0', '2020-carl-von-linne', '2020', 'Carl von Linné', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('ed63b658-8006-38c5-ad8b-3e9c6de32f23', '2021-aristoteles', '2021', 'Aristoteles', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('cc46c8b6-cde5-36c6-8a1c-390adbda58bc', '2022-h-c-andersen', '2022', 'H. C. Andersen', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('712dc0c4-773c-3adb-93db-ea16b1f4b17e', '2023-elisabeth-i', '2023', 'Elisabeth I', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('01bd335e-3c7e-3fb3-a6a6-e1472d8bcc3c', '2023-75-arsjubileum', '2023', '75-årsjubileum', (SELECT id FROM spex_category WHERE uuid = '5e9f6265-3589-3256-ab1c-4e4f05df622d'), 'system', NOW(6)),
       ('61699a17-99fd-391d-9686-c72242b9c4e4', '2024-gauss', '2024', 'Gauss', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('5dc13f75-fc0c-3238-a352-283eb027997a', '2025-ask-och-embla', '2025', 'Ask och Embla', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6)),
       ('8d13183e-5a0a-386c-9614-f5718dc0be90', '2026-abraham-lincoln', '2026', 'Abraham Lincoln', (SELECT id FROM spex_category WHERE uuid = 'ab9f4952-1af6-3b23-808a-8b07c54f4173'), 'system', NOW(6));

-- Revivals, each selecting its parent (MySQL does not let VALUES read the table it inserts into)
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '091fdf4f-af37-3b61-9e4f-d5fb942d91ae', '1968-henrik-8', '1968', id, 'system', NOW(6) FROM spex WHERE uuid = '5400a33b-cf1a-3af1-94b9-2c14b38d311b';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '99844fb5-dcfa-398d-b3bc-1d464d753c06', '1971-montezuma', '1971', id, 'system', NOW(6) FROM spex WHERE uuid = '007223b2-2d66-3df9-8a9d-dabe7dba1f69';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '9e579a97-504a-38d5-ac96-0c46ed72ed8d', '1972-alexander', '1972', id, 'system', NOW(6) FROM spex WHERE uuid = '2f851e18-f58d-3858-bf1d-05201040146d';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'c9f59491-eff0-30fa-ae9a-0e081111b1b8', '1973-richard-iii', '1973', id, 'system', NOW(6) FROM spex WHERE uuid = '5fdc96d4-47c7-369c-8628-36d614972cd3';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'e23ae529-9bd1-30bc-829e-5f3b5c65dc20', '1974-nebukadnesar', '1974', id, 'system', NOW(6) FROM spex WHERE uuid = '556c87ac-943b-3e52-a4ce-84ffaf73e826';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '89b57fb8-097b-3b23-8851-c150dbfc897d', '1975-margareta', '1975', id, 'system', NOW(6) FROM spex WHERE uuid = '9e1af41f-1639-3cbe-bd46-0476131dc47f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '696e00b1-a985-34d3-85cb-b78e4103c00a', '1976-charles-ii', '1976', id, 'system', NOW(6) FROM spex WHERE uuid = 'c37b7433-8b5c-3519-869c-5e98681c533f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '80ec38c0-404e-33e2-95fc-0c98e3c2d1dd', '1977-katarina-ii', '1977', id, 'system', NOW(6) FROM spex WHERE uuid = 'fb1941c2-7acf-33cc-b010-2d061de29d5d';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '1790a66a-1ea0-3ecf-a81a-02b36b48bd89', '1978-caesarion', '1978', id, 'system', NOW(6) FROM spex WHERE uuid = '36beacff-88b1-3149-b1f9-ae1874f10f50';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '939a403a-b0ca-38be-a794-dc7b1183dca9', '1980-george-washington', '1980', id, 'system', NOW(6) FROM spex WHERE uuid = '2e2d3e8f-c750-3955-9517-048133768f98';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'dd683269-3394-36b9-8f8a-9ec3f1f89025', '1981-don-pedro', '1981', id, 'system', NOW(6) FROM spex WHERE uuid = 'c8bb4f61-a9fe-34fa-8d27-75c4b16ffe5f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '5101102e-c5dc-3d98-999f-3a7bb2b453bf', '1982-lionardo-da-vinci', '1982', id, 'system', NOW(6) FROM spex WHERE uuid = '112927e0-072f-3898-83f8-e5ab7562310c';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'be1229db-beef-3390-8667-09ffc6ac3309', '1983-anna', '1983', id, 'system', NOW(6) FROM spex WHERE uuid = 'a89657c9-6225-3100-97e7-77ca9c690d7e';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'e2c82244-2f4c-37c7-9b40-99c3ad7c2ea6', '1985-napoleon', '1985', id, 'system', NOW(6) FROM spex WHERE uuid = '80fefd6f-ab6f-330e-b621-ba9456932e3a';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '9f51ca40-3742-3237-932a-b84a3480c210', '1986-noak', '1986', id, 'system', NOW(6) FROM spex WHERE uuid = 'eb00c43f-f368-31cc-a4c2-8e7a17add042';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'bae92641-3331-32e3-93fd-172ab224c483', '1987-nero', '1987', id, 'system', NOW(6) FROM spex WHERE uuid = 'b5e4fcfb-557d-3ba4-acbb-edbaff4abe51';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'b881daf9-94b9-3140-980a-82ef38b800a5', '1988-gustaf-e-son-vasa', '1988', id, 'system', NOW(6) FROM spex WHERE uuid = 'c3804a4d-3c22-389b-b8ec-bcdae27247eb';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '76ee6b9d-20f5-30f3-8662-5bacce3921b6', '1989-katarina-ii', '1989', id, 'system', NOW(6) FROM spex WHERE uuid = 'fb1941c2-7acf-33cc-b010-2d061de29d5d';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'fbcc5f9f-9b59-3e58-ab73-45bdd9c59176', '1989-turandot', '1989', id, 'system', NOW(6) FROM spex WHERE uuid = 'b50f813d-2dbc-3264-87e1-679cd113f8d9';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'bf961ffa-c158-34ae-9338-b20d2ac2d123', '1990-nils-dacke', '1990', id, 'system', NOW(6) FROM spex WHERE uuid = 'db47475a-1a7e-3b10-826e-d80c1b590a2e';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '948a2a5e-739b-372d-8f6c-1eebac9ff6eb', '1991-sherlock-holmes', '1991', id, 'system', NOW(6) FROM spex WHERE uuid = 'a6ef75df-0565-3a66-9c24-cb5fe4c0d824';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'b472d102-3b32-31cf-8d0e-9476313abdef', '1992-ludwig-van-beethoven', '1992', id, 'system', NOW(6) FROM spex WHERE uuid = '8d2439ee-676f-3c11-8a58-4a62b8468a64';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '64c6fb30-1508-33a6-821f-d18dc3dc3482', '1993-sven-duva', '1993', id, 'system', NOW(6) FROM spex WHERE uuid = '4687283b-3bdf-37c3-99d9-73b06410613a';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '6c388506-b1a7-34df-9ff2-49e02be1e044', '1994-lasse-maja', '1994', id, 'system', NOW(6) FROM spex WHERE uuid = '857c6791-4659-3e6b-bb24-002312ce420b';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '385be2a4-fbc0-35cd-a63a-548a1a552e94', '1995-dr-livingstone', '1995', id, 'system', NOW(6) FROM spex WHERE uuid = 'a5a2ec6f-bab1-339d-83eb-1aed8be77dc5';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '7e1e021c-4536-31ba-8c5c-e63953c87852', '1996-victoria', '1996', id, 'system', NOW(6) FROM spex WHERE uuid = 'b0f1ce7a-6937-345e-b1e0-f7d6bd1e7285';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '01ee8b07-f95e-3db0-9a9c-241badce5e85', '1997-tutankhamon', '1997', id, 'system', NOW(6) FROM spex WHERE uuid = 'c657764e-de66-3ea9-b697-9dbb5b64c44f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '345dc6a3-78d3-3071-844e-b267398147b4', '1998-george-washington', '1998', id, 'system', NOW(6) FROM spex WHERE uuid = '2e2d3e8f-c750-3955-9517-048133768f98';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '4608c879-94b4-3e1c-a18a-5f79ab49c488', '1998-henrik-8', '1998', id, 'system', NOW(6) FROM spex WHERE uuid = '5400a33b-cf1a-3af1-94b9-2c14b38d311b';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'db3b4cbd-2675-3e1b-8519-00b669d71159', '1998-klondike', '1998', id, 'system', NOW(6) FROM spex WHERE uuid = '22916c07-04fc-3b3f-8a07-b1529774417e';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '034058ed-8b54-361f-8dc4-5a712f81317b', '1998-ludwig-van-beethoven', '1998', id, 'system', NOW(6) FROM spex WHERE uuid = '8d2439ee-676f-3c11-8a58-4a62b8468a64';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '48e766c4-6006-3a9f-8771-b4e7b855afb1', '1999-john-ericsson', '1999', id, 'system', NOW(6) FROM spex WHERE uuid = '23802256-46be-3f52-81c2-8dc6ad6a8792';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '2fd9befe-cf02-3bd2-aea1-dbfc1b276480', '2000-bojan', '2000', id, 'system', NOW(6) FROM spex WHERE uuid = 'c733337b-1a27-3264-a00f-dac3ab13e661';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '164c32e9-e6e9-30a5-8f19-1db63d3cc871', '2001-ludvig-xiv', '2001', id, 'system', NOW(6) FROM spex WHERE uuid = '357c6558-451b-38ea-953c-a4e3af2479e6';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '42e7abd8-6e1f-3ac8-ace1-cd41fcafeff5', '2002-anna', '2002', id, 'system', NOW(6) FROM spex WHERE uuid = 'a89657c9-6225-3100-97e7-77ca9c690d7e';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '33e40e69-fdf0-3a9a-9681-975b6fddc929', '2005-montgomery', '2005', id, 'system', NOW(6) FROM spex WHERE uuid = '337a62c3-ab48-3bd4-9d6c-e767624b7eeb';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '0a267fa2-f7f6-398d-a9b4-8551ca3d93d1', '2006-stradivarius', '2006', id, 'system', NOW(6) FROM spex WHERE uuid = '152dfcdc-d576-3c76-85c9-2d7244347c3f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'faa21a0b-9a10-3def-89de-1fb093bd4670', '2007-filip-ii', '2007', id, 'system', NOW(6) FROM spex WHERE uuid = 'c9b7a708-46ab-37a5-9f44-589687e0cb3a';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'd7830994-aa4e-3d2b-b738-e7bf13efb1e8', '2008-caesarion', '2008', id, 'system', NOW(6) FROM spex WHERE uuid = '0bbdcf2a-c1a7-36dc-b055-7bdb444353a0';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'a11697ad-8cb0-3092-bcc5-bc8a661e6203', '2009-svartskagg', '2009', id, 'system', NOW(6) FROM spex WHERE uuid = '0d71786d-1d91-39d2-b712-3488386c75bb';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'cb511ede-fe9a-3724-958b-d40163acccfc', '2010-caesarion', '2010', id, 'system', NOW(6) FROM spex WHERE uuid = '36beacff-88b1-3149-b1f9-ae1874f10f50';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '75ae0806-a1b2-3ba5-ae56-aaf298781a13', '2012-katarina-ii', '2012', id, 'system', NOW(6) FROM spex WHERE uuid = 'fb1941c2-7acf-33cc-b010-2d061de29d5d';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'e98baed3-0eea-32ac-b3e6-c1f46705ef14', '2013-charles-ii', '2013', id, 'system', NOW(6) FROM spex WHERE uuid = 'c37b7433-8b5c-3519-869c-5e98681c533f';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '2c329b16-a70d-3bb8-b69e-f39d18303976', '2013-gagarin', '2013', id, 'system', NOW(6) FROM spex WHERE uuid = '7f749429-11a4-3a84-8458-2ae965672002';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '0a29fa2f-0e40-30a0-9e98-40abfba9bd06', '2013-lasse-maja', '2013', id, 'system', NOW(6) FROM spex WHERE uuid = '857c6791-4659-3e6b-bb24-002312ce420b';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '6d7610d2-0be8-329e-92f3-7c848d635adf', '2013-mata-hari', '2013', id, 'system', NOW(6) FROM spex WHERE uuid = 'e3133b1c-9e73-3f65-94ee-da4acf7912c5';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '4f4e5e37-696c-3fb8-b99b-8bd3423037a4', '2015-victoria', '2015', id, 'system', NOW(6) FROM spex WHERE uuid = 'b0f1ce7a-6937-345e-b1e0-f7d6bd1e7285';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '5bcff9cb-a5b4-3674-bc8e-bcc9026695b2', '2018-christina', '2018', id, 'system', NOW(6) FROM spex WHERE uuid = '319fd3f8-16fd-3bd7-8dbb-6f9e19865df3';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'c148a45e-9b9c-38c5-9f57-c96cedcca391', '2018-richard-iii', '2018', id, 'system', NOW(6) FROM spex WHERE uuid = '5fdc96d4-47c7-369c-8628-36d614972cd3';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT 'd70b0fe1-f13a-344f-b59f-e72cfdd334ef', '2022-snorre', '2022', id, 'system', NOW(6) FROM spex WHERE uuid = '83327344-edb0-313f-94d8-6607fce5f8ed';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '385d38bc-c04a-32d7-8a29-731ffa0b7272', '2024-jeanne-d-arc', '2024', id, 'system', NOW(6) FROM spex WHERE uuid = '547c9be4-97b7-3782-bfee-420503fef010';
INSERT INTO spex (uuid, slug, year, parent_id, created_by, created_at)
SELECT '485930d1-bc72-38f5-b2f1-e5c6386450b7', '2024-krosus', '2024', id, 'system', NOW(6) FROM spex WHERE uuid = 'e77b28a4-1dc2-3155-9583-fc0363d2b8ce';

-- The committee is the board on spex pages; the musicians' tasks are Spexarkivet's instruments
INSERT INTO task_category (uuid, name, actor_present, board, instruments, created_by, created_at)
VALUES ('01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321', 'Kommitté', 0, 1, 0, 'system', NOW(6)),
       ('228a0f16-58f8-3e07-9c7b-9afcfca43464', 'Orkester', 0, 0, 1, 'system', NOW(6)),
       ('9a0f33aa-7941-3213-84ad-29dd9332d15a', 'Ensemble', 1, 0, 0, 'system', NOW(6)),
       ('234b0702-9099-35d0-964c-99de1b6a0aa6', 'Bandet', 0, 0, 1, 'system', NOW(6)),
       ('f0629f8b-2059-3e7a-a28c-55e4c1a4caff', 'Symphonin', 0, 0, 1, 'system', NOW(6)),
       ('2c0e0fde-3db8-36a7-97f6-661d504f682f', 'Annat', 0, 0, 0, 'system', NOW(6));

INSERT INTO task (uuid, name, category_id, created_by, created_at)
VALUES ('9838400c-6364-3a06-90ad-05408fb66d4e', 'Affisch', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('682cf8b6-0e6a-32a1-8753-f830ec219973', 'Arrangör', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('4488bf43-e527-3c91-af4a-aa5f9361e84c', 'Chanteuse', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('958e234d-8059-3ba7-a167-019c056a32dd', 'Conducteur', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('62443f48-c114-37ae-b3b6-fa30da64378c', 'Författare', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('1955afb4-fb09-3030-bfde-925af7d9ace3', 'Koreograf', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('166c54cb-f00c-3aae-ae26-bb5577cd91a3', 'Musikproducent', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('fd46c670-18eb-3805-9edd-007463240704', 'Okänt', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('f0bc613b-23e5-3672-8e42-aef47a1c782a', 'Producent', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('083adf55-7e12-35d2-9cc1-19350f1831a7', 'Regisseur', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('934283f5-1e3c-3966-8cde-3c37c718ddfb', 'Vistextförfattare', (SELECT id FROM task_category WHERE uuid = '2c0e0fde-3db8-36a7-97f6-661d504f682f'), 'system', NOW(6)),
       ('4499201f-e7a8-34c1-b4fa-bb7f54b3e8d0', 'Altsaxofon', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('3f125d50-bddd-35fa-90e8-d60bf305c652', 'Bas', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('7629656f-f459-3c9d-bb93-ad480b433acb', 'Cello', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('26a4c7e7-6187-3ec4-9506-9929760188ee', 'Dragspel', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('ded18303-b162-3eaa-a543-6c2f95005887', 'Elfiol', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('2491671f-06df-35ec-aebb-29270edf4ddc', 'Fiol', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('33b61940-ab3c-3a61-b42f-af5d2beb01d3', 'Flöjt', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('1b8b7d57-97c8-3b81-818d-ae472b61fb9a', 'Gitarr', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('53eea85b-f7e5-32cd-958f-3bf35be8b7a0', 'Klarinett', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('84c9e5ea-93d2-3eb6-a00e-9b01775a606c', 'Mandolin', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('18c1df1c-0acc-3954-9239-169677d1e328', 'Oboe', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('c6e27e55-76bb-3d28-84a8-804b352d109c', 'Okänt', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('0cb389d7-b7a1-3844-91b6-a72509b89256', 'Pianör', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('a1ed44c9-ebf8-3209-b532-d982a86fc071', 'Saxofon', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('56188862-4144-39d6-8e9e-a3640683f62b', 'Tenorsaxofon', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('14fefb6d-48ce-3c10-abb0-83f995f7a6a5', 'Trombon', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('42ca8686-4494-3f29-a986-d1585e07a3c4', 'Trummor', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('d9a1c8a1-ca1c-388d-9424-6fa9f2df3034', 'Trumpet', (SELECT id FROM task_category WHERE uuid = '234b0702-9099-35d0-964c-99de1b6a0aa6'), 'system', NOW(6)),
       ('4d95efdf-539d-3bf9-a604-b7ef53ba9927', 'Ensemblist', (SELECT id FROM task_category WHERE uuid = '9a0f33aa-7941-3213-84ad-29dd9332d15a'), 'system', NOW(6)),
       ('20de6d57-5f49-3941-acc2-173ddbe58d71', 'Arkitekt', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('149ea7b2-4abf-3377-ad53-641bbd650563', 'Barchef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('464cc900-efd6-315a-abc6-56a4beabc863', 'Casseur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('5c8b05f6-9c9e-3246-9e75-e2c0b38b82f6', 'Communicateur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('0b24cca6-0e79-3aa6-94a8-1dd6e7ce11e2', 'Costumeur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('9f0f50ff-823c-3b77-81ae-34109a8dcf64', 'Costumeurchef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('38c6d270-30a6-3c78-a6ed-ec27fa0de54f', 'CR-chef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('3b2eea7c-79df-35b4-b6ec-5de3ff23472f', 'Dekoratör', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('99f1fe34-5b5c-3ab8-8164-3ab04cec7bf8', 'Designer', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('0dfb4fba-8167-3a0f-b960-ce741bc8289d', 'Directeur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('c4d17867-4c7e-32ea-b664-1d18f2b7ff45', 'Directeursassistent', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('30862db9-98ad-3d84-b490-1d471fbf82fd', 'Documentateur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('08d2d429-bbd6-300e-bcc6-758330c0a3cf', 'Exspextor', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('91fa0ceb-1bfd-34be-8eb7-1c8f313fb021', 'Festdirecteur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('05450f37-6e12-33a7-a263-9644ec3dbdea', 'Festfixare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('e4e6e26a-442d-32b0-b8a3-2bd122b101b0', 'Festnisse', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('58d0628a-5872-3d28-9d12-d78e3a1c1a83', 'Festroddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('469e6bbf-cfa0-3925-b924-8ce6f1fb1e6c', 'Fotografiker', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('87a1edbc-1a6c-3894-98c5-a1e7c90106f0', 'Gückeljoursledare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('85c0f999-b34b-307a-93a0-949d81e42de5', 'Info-chef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('9671bb70-24b8-39f5-bdfc-ed4fd4c60227', 'Inspextant', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('15a7c05a-e1ff-3775-974f-d0834036bb69', 'Inspextor', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('27a7ed16-acd2-340d-9302-1a9e262a0538', 'Inspextris', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('5887189d-e30c-3f29-a039-aff21b6d52d9', 'Inspicient', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('ad62f3b3-40f0-3992-994d-2aebe235fc39', 'Inspicientassistent', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('d7388221-4fed-312b-8484-db819c504b4b', 'Kommittéledamot', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('834c8816-5199-3aa9-9b87-09279587437f', 'Kommunikationsansvarig', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('0a2d8dde-2b15-3625-8398-85f9a6ac5c58', 'Konstnärlig ledare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('9a4723f3-c02f-3ec3-8f1f-3a078f8c615a', 'Livestreamer', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('4afdaac2-075e-309d-b45c-bd243eb5d507', 'Ljudmästare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('c7b3a010-2faa-320c-a489-b0ceb6c1a230', 'Ljudroddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('c5fdb06e-1578-3a0a-abe0-1ecb917d59d1', 'Ljusmästare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('527ac5ed-c913-390e-b792-8638e7895b86', 'Ljusroddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('41dd39be-eca7-31f9-8d5c-39a83fa8e0fc', 'Mediaansvarig', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('9dad622b-6935-31f1-ad91-3e9c2e4d8697', 'Mediamästare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('1f96a103-3283-3f23-a774-81b0da6ef2b4', 'Ordförande', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('eb55cd35-93e7-3108-98af-784863ecee71', 'Organisateur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('a8d0f418-285d-3303-a243-1c22a5a00e5d', 'PR-chef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('cdd2e9cd-f996-3b81-924a-4ce654039547', 'PR-roddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('9a630912-c5a1-33df-ac48-77e6dc9e422d', 'PR-strateg', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('2e43510d-e6ea-3c97-9f4c-a714358dab96', 'PR/Info-stab', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('aeb8f939-a351-3d4a-8f90-c5682a15e385', 'Produktionschef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('642dd0c3-5f05-3224-bf8e-d09fce43c7b2', 'Scen', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('386acbcb-3cf0-3b87-b009-28d2d446d74f', 'Scenmästare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('fece8617-0e63-33bb-80d0-4653d40bc7e1', 'Scennisse', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('423037d3-7861-3bf5-989c-637aac19ee40', 'Scenograf', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('cf48e332-ddfa-304b-95b1-159e132db7f7', 'Scenroddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('a776354a-96ae-33a0-a632-c4c0a2ab03b5', 'Sekreterare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('76675762-e14c-3475-817c-50ee57579c7c', 'Skivkommitté', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('0f6cd8eb-948a-3406-8f10-03e30913c0cb', 'Skivocent', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('47632b22-9b8d-364a-ab6a-1814a179130e', 'Skräddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('7a46132f-c618-33ce-8be7-f2a60bf90cbd', 'Spexmästare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('7f395dc7-3c9c-33b4-8e94-5ee2535728ab', 'Sufflör', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('85bff65e-c24e-3fda-b14e-0467035965cc', 'Sömmerska', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('4e3307f9-339a-3821-8077-d81ed71a5e35', 'Tekniknisse', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('b906d2b4-a8c0-3d11-a311-802d7c13aa00', 'Teknikroddare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('46840e52-2620-3140-9327-df334a08adf8', 'Turnéchef', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('79a0cd39-6c1b-3a21-9e29-f815112e3679', 'Turnédirecteur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('029cccdf-a5a1-37d6-8081-24ce48374e3d', 'Upplysare', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('986c5dc4-3184-34d6-84d2-fe53443a4238', 'Vice Directeur', (SELECT id FROM task_category WHERE uuid = '01c7f0bc-9dc9-30ef-99c8-c70fb2e3b321'), 'system', NOW(6)),
       ('7ff46964-5a9f-35b3-a60d-95dd3dba3d91', 'Bas', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('5c657e61-c017-344b-bb40-07f76509bc2f', 'Basklarinett', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('06b43991-55dd-336d-a79f-dd55740e7403', 'Bastuba', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('6739e0d0-3617-3c2d-a397-23d84d108a6e', 'Blockflöjt', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('4d226f13-8357-3bee-8f7b-1fe7414523e1', 'Cello', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('66aef77b-5288-31c3-8200-e9312367fb2c', 'Cello 1', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('e03547f6-4f47-3840-beab-5328d540ff6e', 'Cello 2', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('91c0ce66-705e-3fbb-a2c5-fce6627eb78b', 'Fagott', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('20304da9-0310-327d-947b-0622bb5da070', 'Flöjt', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('846df568-9f38-35a0-af96-f61429ae0b48', 'Gitarr', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('eea5d31c-0f92-34bd-95ac-e000d95af9bc', 'Horn', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('ba1589e0-76f8-3c16-b36e-64f3633f6199', 'Kapellmästare', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('c9b7d59f-a4ee-3b98-b2db-dfc6ddc516e8', 'Klarinett', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('ba391cd3-0fb1-38a3-af60-339adfe9b98c', 'Kontrabas', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('0748d53c-ef31-3ed5-a659-fe9a4734b377', 'Oboe', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('6bc37849-6e2b-3cb6-a589-9096880d9375', 'Oboeflöjt', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('e47e2093-2085-35c3-add6-1cdedd9c69c6', 'Okänt', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('8f6e49ef-b4a1-30af-9513-98cee780b779', 'Orkester', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('34f31b82-2a92-302e-b152-d5e0141ed8ff', 'Panblåsare', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('974e1077-db3c-3b09-bcf9-4adb48f5c607', 'Pianör', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('ba2647e8-29f2-3b0d-8231-44423fac073b', 'Saxofon', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('7a78acbf-da6b-3dd8-adc0-d4602d7822a0', 'Slagverk', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('78bfaa19-ec67-3f8c-8520-25dbb113222f', 'Sopransaxofon', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('449c29e3-20aa-3a98-bb72-e1a149cae4d9', 'Trombon', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('08de5db5-e734-3dca-8f92-0555db31f196', 'Trummor', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('9b1f41ba-64bd-3d60-beea-c39229d0900e', 'Trumpet', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('5401fa59-d407-3379-a36e-86d0413cade9', 'Tvärflöjt', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('52beeb03-b41b-37bc-9cb2-a74f4ab2eb3b', 'Valthorn', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('bfeefe1a-90a8-3105-9e64-77996947cbe3', 'Viola', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('68dee049-de8a-32b2-9807-e646bc98fc9e', 'Violacello', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('eb94a763-caa6-338a-bf97-d6d6413e8193', 'Violin', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('b89b56e1-a3d3-3718-8bd1-b5bb87ea5aee', 'Violin 1', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('783715bd-aa92-3dee-bbeb-9f3a37b2ebcd', 'Violin 2', (SELECT id FROM task_category WHERE uuid = '228a0f16-58f8-3e07-9c7b-9afcfca43464'), 'system', NOW(6)),
       ('6a361344-1e4b-308a-bc32-7d2abcd49a7e', 'Althorn', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('2f237425-699b-397a-9fbd-f943bf996277', 'Altsaxofon', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('8795862a-7bc5-341a-a232-f815f59dc9f6', 'Bas', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('4426a391-e482-39ac-a962-a7f31b283abf', 'Cello', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('47a3c38e-4b6f-3a54-8189-ab78f0a126fb', 'Elbas', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('10535b19-35c2-360b-8e56-f6739552aa74', 'Elgitarr', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('eaba342e-d41b-340c-a188-b62192d5c15e', 'Fiol', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('4d0bf571-d964-377e-9638-d70b2c9aa58c', 'Gitarr', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('2718758e-c55c-3b71-a0cb-89ca396dceca', 'Kapellmästare', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('d5775b07-8de1-30c8-b9b8-e705802fd0b3', 'Klarinett', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('349f3360-31a6-32ad-8a6e-2c2b1c227eba', 'Okänt', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('fcb73d59-4b56-37e0-99eb-1277dfd84670', 'Pianör', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('fb908cb9-fe3b-3c54-895f-0ceaeb800af7', 'Saxofon', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('19e0f0d4-768f-322b-9df7-45d848c29244', 'Slagverk', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('9c208bf7-3392-3f5c-a23d-eb02a30db151', 'Trummor', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('a305f728-89a8-3f13-a07f-212cbf42ce0d', 'Trumpet', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('c436b973-1f40-3d26-868c-b3f240bf7cb2', 'Tvärflöjt', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('f45894c4-abcb-3b59-b20d-d0a74a06d3b8', 'Valthorn', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('bd20bdb5-e45a-3fd8-8f4e-99d314125822', 'Viola', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('2a255dde-5be1-3f86-b1b6-5c1314798198', 'Violin 1', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6)),
       ('9ea2b9bd-3602-3013-8b96-613c9447df05', 'Violin 2', (SELECT id FROM task_category WHERE uuid = 'f0629f8b-2059-3e7a-a28c-55e4c1a4caff'), 'system', NOW(6));
