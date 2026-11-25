CREATE TABLE tipo (
    id integer PRIMARY KEY CHECK (id > 0),
    tipo varchar(100) NOT NULL CHECK (length(trim(tipo)) > 0)
);

CREATE TABLE calendario (
    id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY CHECK (id > 0),
    fecha date NOT NULL UNIQUE,
    id_tipo integer NOT NULL REFERENCES tipo(id) ON DELETE RESTRICT,
    descripcion varchar(20) NOT NULL
);

INSERT INTO tipo (id, tipo) VALUES
    (1, 'Dia laboral'),
    (2, 'Fin de Semana'),
    (3, 'Dia festivo');
