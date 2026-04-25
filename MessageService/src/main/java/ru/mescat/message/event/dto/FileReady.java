package ru.mescat.message.event.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import ru.mescat.message.dto.FileDto;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FileReady {

    private FileDto file;
}
